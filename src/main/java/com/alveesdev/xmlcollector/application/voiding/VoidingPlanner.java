package com.alveesdev.xmlcollector.application.voiding;

import java.io.IOException;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.alveesdev.xmlcollector.application.collection.CollectionResult;
import com.alveesdev.xmlcollector.application.collection.SourceFailure;
import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.SearchFolderPattern;
import com.alveesdev.xmlcollector.domain.model.SeriesOwner;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.service.MissingNumberFinder;

public final class VoidingPlanner {

	private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

	private final IssuedNumberRepository issuedNumbers;

	public VoidingPlanner(IssuedNumberRepository issuedNumbers) {
		this.issuedNumbers = issuedNumbers;
	}

	// Anything here means a note may exist without having been read, so a missing number cannot be trusted.
	public static List<String> problemsOf(CollectionResult result) {

		List<String> problems = new ArrayList<>();

		for (SourceFailure failure : result.failures()) {
			problems.add("PDV " + (failure.sourceIndex() + 1) + ": " + failure.path());
		}

		if (result.summary().errors() > 0) {
			problems.add(result.summary().errors() + " arquivo(s) com erro de leitura");
		}

		if (!result.summary().copyFailures().isEmpty()) {
			problems.add(result.summary().copyFailures().size() + " arquivo(s) que não foram copiados");
		}

		if (result.historyFailure() != null) {
			problems.add(result.historyFailure());
		}

		return problems;
	}

	public static Map<Integer, List<Path>> pdvFolders(List<VoidingRequest> requests, List<String> directories,
			Map<Integer, Set<Integer>> sourcesBySeries, String folderPattern, YearMonth month) throws VoidingException {

		if (folderPattern.isBlank()) {
			throw new VoidingException("O modelo de busca não tem pasta de inutilização para guardar o XML no PDV.");
		}

		Map<Integer, List<Path>> folders = new HashMap<>();

		for (VoidingRequest request : requests) {
			String relative = folderPattern
					.replace(SearchFolderPattern.CNPJ, request.cnpj())
					.replace(SearchFolderPattern.YEAR_MONTH, YEAR_MONTH_FORMAT.format(month));

			List<Path> ofSeries = new ArrayList<>();

			for (int source : new TreeSet<>(sourcesBySeries.getOrDefault(request.series(), Set.of()))) {
				if (source < directories.size() && !directories.get(source).isBlank()) {
					ofSeries.add(Path.of(directories.get(source).trim()).resolve(relative));
				}
			}

			if (ofSeries.isEmpty()) {
				throw new VoidingException("Não foi possível identificar o PDV da série " + request.series() + " para guardar o XML.");
			}

			folders.put(request.series(), ofSeries);
		}

		return folders;
	}

	public List<VoidingRequest> plan(Map<Integer, List<NumberRange>> wanted, Map<Integer, List<NumberRange>> missing,
			Map<Integer, Set<SeriesOwner>> owners) throws VoidingException {

		List<VoidingRequest> requests = new ArrayList<>();

		for (Map.Entry<Integer, List<NumberRange>> entry : wanted.entrySet()) {
			int series = entry.getKey();
			SeriesOwner owner = ownerOf(series, owners);

			for (NumberRange range : entry.getValue()) {
				requireMissing(series, range, missing);
				requireNeverIssued(series, range, owner);
				requests.add(request(series, range, owner));
			}
		}

		if (requests.isEmpty()) {
			throw new VoidingException("Não há números faltantes para inutilizar.");
		}

		return requests;
	}

	private static SeriesOwner ownerOf(int series, Map<Integer, Set<SeriesOwner>> owners) throws VoidingException {

		Set<SeriesOwner> ofSeries = owners.getOrDefault(series, Set.of());

		if (ofSeries.isEmpty()) {
			throw new VoidingException("A série " + series + " não apareceu na última coleta.");
		}

		if (ofSeries.size() > 1) {
			throw new VoidingException("A série " + series + " tem notas de mais de um CNPJ, estado ou ano. "
					+ "Colete um período de um único ano e confira os diretórios.");
		}

		return ofSeries.iterator().next();
	}

	private static void requireMissing(int series, NumberRange range, Map<Integer, List<NumberRange>> missing) throws VoidingException {

		List<NumberRange> outside = MissingNumberFinder.subtract(List.of(range), missing.getOrDefault(series, List.of()));

		if (!outside.isEmpty()) {
			throw new VoidingException("Série " + series + ": o número " + outside.get(0).start()
					+ " não está na lista de faltantes, então não pode ser inutilizado.");
		}
	}

	private void requireNeverIssued(int series, NumberRange range, SeriesOwner owner) throws VoidingException {
		try {
			Set<Integer> issued = issuedNumbers.issuedIn(owner.cnpj(), DocumentModel.NFCE.code(), series, range);

			if (!issued.isEmpty()) {
				throw new VoidingException("Série " + series + ": o número " + issued.iterator().next()
						+ " já foi coletado antes e está no histórico, então não pode ser inutilizado.");
			}
		} catch (IOException e) {
			throw new VoidingException("Não foi possível consultar o histórico de numerações: " + e.getMessage(), e);
		}
	}

	private static VoidingRequest request(int series, NumberRange range, SeriesOwner owner) throws VoidingException {
		try {
			return new VoidingRequest(owner.ufCode(), owner.year(), owner.cnpj(), DocumentModel.NFCE, series, range,
					VoidingRequest.STANDARD_JUSTIFICATION);
		} catch (IllegalArgumentException e) {
			throw new VoidingException("Dados inválidos para inutilizar a série " + series + ": " + e.getMessage(), e);
		}
	}
}
