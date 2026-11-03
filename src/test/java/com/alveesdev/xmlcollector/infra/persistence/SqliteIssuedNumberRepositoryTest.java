package com.alveesdev.xmlcollector.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.domain.model.IssuedNumber;

class SqliteIssuedNumberRepositoryTest {

	private static final String CNPJ = "12345678000190";
	private static final String OTHER_CNPJ = "98765432000110";
	private static final String NFCE = "65";
	private static final String NFE = "55";

	@TempDir
	Path directory;

	private Path file;
	private SqliteIssuedNumberRepository repository;

	@BeforeEach
	void setUp() {
		file = directory.resolve("Database").resolve("coletor.db");
		repository = new SqliteIssuedNumberRepository(file);
	}

	@Test
	void createsTheDatabaseAndStoresTheNumbers() throws Exception {
		repository.saveAll(List.of(new IssuedNumber(CNPJ, NFCE, 1, 2)));

		assertEquals(List.of(new IssuedNumber(CNPJ, NFCE, 1, 2)), stored());
	}

	@Test
	void initializingCreatesAnEmptyDatabaseWithItsTable() throws Exception {
		repository.initialize();

		assertTrue(Files.isRegularFile(file));
		assertEquals(List.of(), stored());
	}

	@Test
	void initializingAgainKeepsWhatWasSaved() throws Exception {
		repository.saveAll(List.of(new IssuedNumber(CNPJ, NFCE, 1, 2)));

		new SqliteIssuedNumberRepository(file).initialize();

		assertEquals(List.of(new IssuedNumber(CNPJ, NFCE, 1, 2)), stored());
	}

	@Test
	void reportsWhenTheDatabaseCannotBeCreated() throws Exception {
		Files.createDirectories(file);

		assertThrows(IOException.class, repository::initialize);
	}

	@Test
	void keepsWhatWasSavedBefore() throws Exception {
		repository.saveAll(List.of(new IssuedNumber(CNPJ, NFCE, 1, 1)));
		new SqliteIssuedNumberRepository(file).saveAll(List.of(new IssuedNumber(CNPJ, NFCE, 1, 2)));

		assertEquals(List.of(new IssuedNumber(CNPJ, NFCE, 1, 1), new IssuedNumber(CNPJ, NFCE, 1, 2)), stored());
	}

	@Test
	void collectingTheSameNumberAgainDoesNotDuplicateIt() throws Exception {
		repository.saveAll(List.of(new IssuedNumber(CNPJ, NFCE, 1, 1)));
		repository.saveAll(List.of(new IssuedNumber(CNPJ, NFCE, 1, 1), new IssuedNumber(CNPJ, NFCE, 1, 2)));

		assertEquals(2, stored().size());
	}

	@Test
	void treatsTheSameNumberInAnotherSeriesCnpjOrModelAsDifferent() throws Exception {
		List<IssuedNumber> numbers = List.of(
				new IssuedNumber(CNPJ, NFE, 1, 1),
				new IssuedNumber(CNPJ, NFCE, 1, 1),
				new IssuedNumber(CNPJ, NFCE, 2, 1),
				new IssuedNumber(OTHER_CNPJ, NFCE, 1, 1));

		repository.saveAll(numbers);

		assertEquals(numbers, stored());
	}

	@Test
	void savesManyNumbers() throws Exception {
		List<IssuedNumber> numbers = new ArrayList<>();

		for (int number = 1; number <= 5000; number++) {
			numbers.add(new IssuedNumber(CNPJ, NFCE, 1, number));
		}

		repository.saveAll(numbers);

		assertEquals(numbers, stored());
	}

	@Test
	void doesNotCreateTheDatabaseWithNothingToSave() throws Exception {
		repository.saveAll(List.of());

		assertFalse(Files.exists(file));
	}

	@Test
	void reportsAFailureAsIOException() throws Exception {
		Files.createDirectories(file);

		assertThrows(IOException.class, () -> repository.saveAll(List.of(new IssuedNumber(CNPJ, NFCE, 1, 1))));
	}

	private List<IssuedNumber> stored() throws SQLException {
		List<IssuedNumber> numbers = new ArrayList<>();

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery(
						"SELECT cnpj, model, series, number FROM issued_numbers ORDER BY cnpj, model, series, number")) {

			while (rows.next()) {
				numbers.add(new IssuedNumber(rows.getString(1), rows.getString(2), rows.getInt(3), rows.getInt(4)));
			}
		}

		return numbers;
	}
}
