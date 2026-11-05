package com.alveesdev.xmlcollector.infra.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.domain.model.IssuedNumber;
import com.alveesdev.xmlcollector.domain.model.NumberRange;

public class SqliteIssuedNumberRepository implements IssuedNumberRepository {

	private static final String CREATE_TABLE = """
			CREATE TABLE IF NOT EXISTS issued_numbers (
				cnpj TEXT NOT NULL,
				model TEXT NOT NULL,
				series INTEGER NOT NULL,
				number INTEGER NOT NULL,
				issued_on TEXT,
				PRIMARY KEY (cnpj, model, series, number)
			) WITHOUT ROWID""";

	private static final String DATE_COLUMN = "issued_on";

	private static final String ADD_DATE_COLUMN = "ALTER TABLE issued_numbers ADD COLUMN " + DATE_COLUMN + " TEXT";

	// Collecting a month again fills in the date of what was saved before the column existed.
	private static final String INSERT = """
			INSERT INTO issued_numbers (cnpj, model, series, number, issued_on)
			VALUES (?, ?, ?, ?, ?)
			ON CONFLICT (cnpj, model, series, number)
			DO UPDATE SET issued_on = COALESCE(excluded.issued_on, issued_on)""";

	private static final String SELECT_LAST_BEFORE = """
			SELECT number, issued_on FROM issued_numbers
			WHERE cnpj = ? AND model = ? AND series = ? AND number < ?
			ORDER BY number DESC LIMIT 1""";

	private static final String SELECT_IN_RANGE = """
			SELECT number FROM issued_numbers
			WHERE cnpj = ? AND model = ? AND series = ? AND number BETWEEN ? AND ?""";

	private final Path file;

	public SqliteIssuedNumberRepository(Path file) {
		this.file = file;
	}

	@Override
	public void initialize() throws IOException {

		try (Connection connection = open()) {
			// Opening is what prepares the database.
		} catch (SQLException e) {
			throw new IOException("Não foi possível criar o banco de dados: " + e.getMessage(), e);
		}
	}

	@Override
	public void saveAll(Collection<IssuedNumber> numbers) throws IOException {

		if (numbers.isEmpty()) {
			return;
		}

		try (Connection connection = open()) {
			insert(connection, numbers);
		} catch (SQLException e) {
			throw new IOException("Não foi possível gravar no banco de dados: " + e.getMessage(), e);
		}
	}

	@Override
	public Set<Integer> issuedIn(String cnpj, String modelCode, int series, NumberRange range) throws IOException {

		try (Connection connection = open()) {

			try (PreparedStatement statement = connection.prepareStatement(SELECT_IN_RANGE)) {
				statement.setString(1, cnpj);
				statement.setString(2, modelCode);
				statement.setInt(3, series);
				statement.setInt(4, range.start());
				statement.setInt(5, range.end());

				Set<Integer> numbers = new TreeSet<>();

				try (ResultSet rows = statement.executeQuery()) {
					while (rows.next()) {
						numbers.add(rows.getInt(1));
					}
				}

				return numbers;
			}
		} catch (SQLException e) {
			throw new IOException("Não foi possível consultar o banco de dados: " + e.getMessage(), e);
		}
	}

	@Override
	public Optional<IssuedNumber> lastBefore(String cnpj, String modelCode, int series, int number) throws IOException {

		try (Connection connection = open()) {

			try (PreparedStatement statement = connection.prepareStatement(SELECT_LAST_BEFORE)) {
				statement.setString(1, cnpj);
				statement.setString(2, modelCode);
				statement.setInt(3, series);
				statement.setInt(4, number);

				try (ResultSet rows = statement.executeQuery()) {
					if (!rows.next()) {
						return Optional.empty();
					}

					String date = rows.getString(2);

					return Optional.of(new IssuedNumber(cnpj, modelCode, series, rows.getInt(1), date == null ? null : LocalDate.parse(date)));
				}
			}
		} catch (SQLException | DateTimeParseException e) {
			throw new IOException("Não foi possível consultar o banco de dados: " + e.getMessage(), e);
		}
	}

	private Connection open() throws IOException, SQLException {
		Files.createDirectories(file.getParent());

		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);

		try {
			createTable(connection);
			return connection;
		} catch (SQLException e) {
			connection.close();
			throw e;
		}
	}

	private static void createTable(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(CREATE_TABLE);

			if (!hasDateColumn(statement)) {
				statement.execute(ADD_DATE_COLUMN);
			}
		}
	}

	private static boolean hasDateColumn(Statement statement) throws SQLException {
		try (ResultSet columns = statement.executeQuery("PRAGMA table_info(issued_numbers)")) {
			while (columns.next()) {
				if (DATE_COLUMN.equals(columns.getString("name"))) {
					return true;
				}
			}

			return false;
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
				statement.setString(5, number.date() == null ? null : number.date().toString());
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
