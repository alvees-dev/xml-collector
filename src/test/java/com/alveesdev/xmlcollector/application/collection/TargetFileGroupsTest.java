package com.alveesdev.xmlcollector.application.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class TargetFileGroupsTest {

	@Test
	void jobsWithDifferentTargetsRunApart() {
		assertEquals(List.of(List.of(0), List.of(1)), TargetFileGroups.of(List.of(List.of("a"), List.of("b"))));
	}

	@Test
	void jobsWithTheSameTargetRunTogetherInOrder() {
		assertEquals(List.of(List.of(0, 2), List.of(1)), TargetFileGroups.of(List.of(List.of("a"), List.of("b"), List.of("a"))));
	}

	@Test
	void aThirdJobJoinsTheOtherTwo() {
		assertEquals(List.of(List.of(0, 1, 2)), TargetFileGroups.of(List.of(List.of("a"), List.of("b"), List.of("a", "b"))));
	}

	@Test
	void theSecondTargetOfAJobAlsoCounts() {
		assertEquals(List.of(List.of(0, 1)), TargetFileGroups.of(List.of(List.of("a", "chave"), List.of("b", "chave"))));
	}

	@Test
	void noJobsMakeNoGroups() {
		assertEquals(List.of(), TargetFileGroups.of(List.of()));
	}
}
