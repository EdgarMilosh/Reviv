package lt.reviv.bendra.klaidos;

import java.util.Map;

/** Vienodas klaidos atsakymas: bendras pranešimas ir klaidos prie laukų. */
public record KlaidosAtsakymas(String pranesimas, Map<String, String> laukai) {}