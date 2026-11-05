package com.alveesdev.xmlcollector.application.collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Two jobs that can write the same file must run one after the other, including when they only share it through a third job.
final class TargetFileGroups {

	private TargetFileGroups() {
	}

	static List<List<Integer>> of(List<List<String>> targetsOfEachJob) {

		int[] parent = new int[targetsOfEachJob.size()];

		for (int index = 0; index < parent.length; index++) {
			parent[index] = index;
		}

		Map<String, Integer> owner = new HashMap<>();

		for (int index = 0; index < parent.length; index++) {
			for (String target : targetsOfEachJob.get(index)) {
				Integer previous = owner.putIfAbsent(target, index);

				if (previous != null) {
					parent[find(parent, index)] = find(parent, previous);
				}
			}
		}

		Map<Integer, List<Integer>> groups = new LinkedHashMap<>();

		for (int index = 0; index < parent.length; index++) {
			groups.computeIfAbsent(find(parent, index), root -> new ArrayList<>()).add(index);
		}

		return new ArrayList<>(groups.values());
	}

	private static int find(int[] parent, int index) {
		int current = index;

		while (parent[current] != current) {
			parent[current] = parent[parent[current]];
			current = parent[current];
		}

		return current;
	}
}
