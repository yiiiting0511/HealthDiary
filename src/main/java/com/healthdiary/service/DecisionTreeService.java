package com.healthdiary.service;

import com.healthdiary.entity.HealthLog;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Implements the C4.5-style decision tree described in the project spec:
 *   1. Compute overall entropy of the labeled seed/training data.
 *   2. Compute the Information Gain of sleep_hours, steps and mood_score
 *      (continuous attributes -> evaluated at every midpoint between
 *      sorted distinct values, exactly like C4.5 handles numeric attributes).
 *   3. Pick the feature with the highest Information Gain as the split.
 *   4. Recurse on the two partitions until a stopping rule is hit.
 *   5. Use the resulting tree to predict risk_level for new entries.
 */
@Service
public class DecisionTreeService {

    private static final int MAX_DEPTH = 4;
    private static final int MIN_SAMPLES_TO_SPLIT = 4;
    private static final double MIN_GAIN = 1e-9;

    private volatile TreeNode root;
    private volatile TrainingSummary lastSummary;

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public synchronized TrainingSummary train(List<HealthLog> seedLogs) {
        List<Sample> samples = new ArrayList<>();
        for (HealthLog log : seedLogs) {
            if (log.getRiskLevel() == null || log.getRiskLevel().isBlank()) {
                continue;
            }
            samples.add(new Sample(log.getSleepHours(), log.getSteps(), log.getMoodScore(), log.getRiskLevel()));
        }

        if (samples.isEmpty()) {
            this.root = null;
            this.lastSummary = new TrainingSummary(0.0, Map.of(), null, null, 0);
            return this.lastSummary;
        }

        double baseEntropy = entropy(samples);

        FeatureSplit sleepSplit = bestSplit(samples, Sample::sleepHours);
        FeatureSplit stepsSplit = bestSplit(samples, Sample::steps);
        FeatureSplit moodSplit = bestSplit(samples, Sample::moodScore);

        Map<String, FeatureSplit> gains = new LinkedHashMap<>();
        gains.put("sleep_hours", sleepSplit);
        gains.put("steps", stepsSplit);
        gains.put("mood_score", moodSplit);

        String rootFeature = bestFeature(gains);

        this.root = buildTree(samples, 0);
        this.lastSummary = new TrainingSummary(
                baseEntropy,
                toGainMap(gains),
                rootFeature,
                rootFeature == null ? null : gains.get(rootFeature).threshold,
                samples.size()
        );
        return this.lastSummary;
    }

    public String predict(double sleepHours, int steps, int moodScore) {
        if (root == null) {
            // Fallback heuristic used only if the tree has never been trained.
            if (sleepHours < 5 || steps < 3000 || moodScore <= 3) return "High";
            if (sleepHours < 7 || steps < 7000 || moodScore <= 6) return "Medium";
            return "Low";
        }
        TreeNode node = root;
        while (!node.leaf) {
            double value = switch (node.feature) {
                case "sleep_hours" -> sleepHours;
                case "steps" -> steps;
                case "mood_score" -> moodScore;
                default -> 0;
            };
            node = (value <= node.threshold) ? node.left : node.right;
        }
        return node.predictedLabel;
    }

    public boolean isTrained() {
        return root != null;
    }

    public TrainingSummary getLastSummary() {
        return lastSummary;
    }

    public Map<String, Object> getTreeAsJson() {
        if (root == null) {
            return Map.of("trained", false);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("trained", true);
        json.put("entropy", lastSummary.datasetEntropy());
        json.put("informationGain", lastSummary.informationGain());
        json.put("rootFeature", lastSummary.rootFeature());
        json.put("rootThreshold", lastSummary.rootThreshold());
        json.put("sampleCount", lastSummary.sampleCount());
        json.put("tree", nodeToJson(root));
        return json;
    }

    // ---------------------------------------------------------------
    // Tree construction
    // ---------------------------------------------------------------

    private TreeNode buildTree(List<Sample> samples, int depth) {
        double ent = entropy(samples);
        Map<String, Long> counts = classCounts(samples);
        String majority = majorityLabel(counts);

        boolean pure = ent <= 1e-9;
        boolean tooSmall = samples.size() < MIN_SAMPLES_TO_SPLIT;
        boolean tooDeep = depth >= MAX_DEPTH;

        if (pure || tooSmall || tooDeep) {
            return TreeNode.leafNode(majority, ent, counts, samples.size());
        }

        FeatureSplit sleepSplit = bestSplit(samples, Sample::sleepHours);
        FeatureSplit stepsSplit = bestSplit(samples, Sample::steps);
        FeatureSplit moodSplit = bestSplit(samples, Sample::moodScore);

        Map<String, FeatureSplit> gains = new LinkedHashMap<>();
        gains.put("sleep_hours", sleepSplit);
        gains.put("steps", stepsSplit);
        gains.put("mood_score", moodSplit);

        String feature = bestFeature(gains);
        if (feature == null || gains.get(feature).gain <= MIN_GAIN) {
            return TreeNode.leafNode(majority, ent, counts, samples.size());
        }

        FeatureSplit chosen = gains.get(feature);
        List<Sample> left = new ArrayList<>();
        List<Sample> right = new ArrayList<>();
        for (Sample s : samples) {
            if (featureValue(s, feature) <= chosen.threshold) {
                left.add(s);
            } else {
                right.add(s);
            }
        }

        if (left.isEmpty() || right.isEmpty()) {
            return TreeNode.leafNode(majority, ent, counts, samples.size());
        }

        TreeNode node = TreeNode.splitNode(feature, chosen.threshold, ent, counts, samples.size());
        node.left = buildTree(left, depth + 1);
        node.right = buildTree(right, depth + 1);
        return node;
    }

    private double featureValue(Sample s, String feature) {
        return switch (feature) {
            case "sleep_hours" -> s.sleepHours();
            case "steps" -> s.steps();
            case "mood_score" -> s.moodScore();
            default -> 0;
        };
    }

    private String bestFeature(Map<String, FeatureSplit> gains) {
        String best = null;
        double bestGain = -1;
        for (Map.Entry<String, FeatureSplit> e : gains.entrySet()) {
            if (e.getValue().threshold != null && e.getValue().gain > bestGain) {
                bestGain = e.getValue().gain;
                best = e.getKey();
            }
        }
        return best;
    }

    private Map<String, Double> toGainMap(Map<String, FeatureSplit> gains) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, FeatureSplit> e : gains.entrySet()) {
            result.put(e.getKey(), e.getValue().gain);
        }
        return result;
    }

    // ---------------------------------------------------------------
    // Entropy / Information Gain
    // ---------------------------------------------------------------

    private double entropy(List<Sample> samples) {
        if (samples.isEmpty()) return 0.0;
        Map<String, Long> counts = classCounts(samples);
        double total = samples.size();
        double ent = 0.0;
        for (long c : counts.values()) {
            double p = c / total;
            ent -= p * (Math.log(p) / Math.log(2));
        }
        return ent;
    }

    private Map<String, Long> classCounts(List<Sample> samples) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Sample s : samples) {
            counts.merge(s.riskLevel(), 1L, Long::sum);
        }
        return counts;
    }

    private String majorityLabel(Map<String, Long> counts) {
        String best = null;
        long bestCount = -1;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * Finds the best threshold (midpoint between sorted distinct values) for a
     * continuous feature, maximizing Information Gain — the C4.5 approach to
     * splitting numeric attributes.
     */
    private FeatureSplit bestSplit(List<Sample> samples, java.util.function.ToDoubleFunction<Sample> fn) {
        double baseEntropy = entropy(samples);
        TreeSet<Double> distinct = new TreeSet<>();
        for (Sample s : samples) distinct.add(fn.applyAsDouble(s));
        List<Double> sorted = new ArrayList<>(distinct);

        Double bestThreshold = null;
        double bestGain = -1;

        for (int i = 0; i < sorted.size() - 1; i++) {
            double t = (sorted.get(i) + sorted.get(i + 1)) / 2.0;
            List<Sample> left = new ArrayList<>();
            List<Sample> right = new ArrayList<>();
            for (Sample s : samples) {
                if (fn.applyAsDouble(s) <= t) left.add(s); else right.add(s);
            }
            if (left.isEmpty() || right.isEmpty()) continue;

            double weighted = ((double) left.size() / samples.size()) * entropy(left)
                    + ((double) right.size() / samples.size()) * entropy(right);
            double gain = baseEntropy - weighted;
            if (gain > bestGain) {
                bestGain = gain;
                bestThreshold = t;
            }
        }
        return new FeatureSplit(bestThreshold, bestThreshold == null ? 0.0 : bestGain);
    }

    private Map<String, Object> nodeToJson(TreeNode node) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("entropy", round(node.entropy));
        json.put("sampleCount", node.sampleCount);
        json.put("classCounts", node.classCounts);
        if (node.leaf) {
            json.put("leaf", true);
            json.put("predictedLabel", node.predictedLabel);
        } else {
            json.put("leaf", false);
            json.put("feature", node.feature);
            json.put("threshold", round(node.threshold));
            json.put("left", nodeToJson(node.left));
            json.put("right", nodeToJson(node.right));
        }
        return json;
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    // ---------------------------------------------------------------
    // Value types
    // ---------------------------------------------------------------

    private record Sample(double sleepHours, int steps, int moodScore, String riskLevel) {
    }

    private record FeatureSplit(Double threshold, double gain) {
    }

    public record TrainingSummary(
            double datasetEntropy,
            Map<String, Double> informationGain,
            String rootFeature,
            Double rootThreshold,
            int sampleCount
    ) {
    }

    private static class TreeNode {
        boolean leaf;
        String feature;
        Double threshold;
        String predictedLabel;
        double entropy;
        Map<String, Long> classCounts;
        int sampleCount;
        TreeNode left;
        TreeNode right;

        static TreeNode leafNode(String label, double entropy, Map<String, Long> counts, int sampleCount) {
            TreeNode n = new TreeNode();
            n.leaf = true;
            n.predictedLabel = label;
            n.entropy = entropy;
            n.classCounts = counts;
            n.sampleCount = sampleCount;
            return n;
        }

        static TreeNode splitNode(String feature, double threshold, double entropy, Map<String, Long> counts, int sampleCount) {
            TreeNode n = new TreeNode();
            n.leaf = false;
            n.feature = feature;
            n.threshold = threshold;
            n.entropy = entropy;
            n.classCounts = counts;
            n.sampleCount = sampleCount;
            return n;
        }
    }
}
