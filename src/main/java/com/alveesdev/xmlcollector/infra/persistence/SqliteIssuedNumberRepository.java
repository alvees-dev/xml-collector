package com.alveesdev.xmlcollector.infra.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.domain.model.IssuedNumber;

public class SqliteIssuedNumberRepository implements IssuedNumberRepository {

	private static final String CREATE_TABLE = """
			CREATE TABLE IF NOT EXISTS issued_numbers (
				cnpj TEXT NOT NULL,
				model TEXT NOT NULL,
				series INTEGER NOT NULL,
				number INTEGER NOT NULL,
				PRIMARY KEY (cnpj, model, series, number)
			) WITHOUT ROWID""";

	private static final String INSERT = """
			INSERT OR IGNORE INTO issued_numbers (cnpj, model, series, number)
			VALUES (?, ?, ?, ?)""";

	private final Path file;

	public SqliteIssuedNumberRepository(Path file) {
		this.file = file;
	}

	@Override
	public void saveAll(Collection<IssuedNumber> numbers) throws IOException {

		if (numbers.isEmpty()) {
			return;
		}

		Files.createDirectories(file.getParent());

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
			createTable(connection);
			insert(connection, numbers);
		} catch (SQLException e) {
			throw new IOException("Não foi possível gravar no banco de dados: " + e.getMessage(), e);
		}
	}

	private static void createTable(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(CREATE_TABLE);
		}
	}

	private static void insert(Connection connection, Collection<IssuedNumber> numbers) throws SQLException {
		connection.setAutoCommit(false);

		try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
			for (IssuedNumber number : numbers) {
				statement.setString(1, number.cnpj());
				statement.setString(2, number.modelCode());
				statement.setInt(3, number.series());
				statement.setInt(4, number.number());
				statement.addBatch();
			}

			statement.executeBatch();
			connection.commit();

		} catch (SQLException e) {
			connection.rollback();
			throw e;
		}
	}
}
