package sv.edu.ues.fmp.flora.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Textos {

    private Textos() {
    }

    /**
     * Quita espacios al inicio y al final y colapsa espacios internos a uno.
     * Devuelve null si el resultado queda vacio.
     */
    public static String normalizar(String valor) {
        if (valor == null) return null;
        String limpio = valor.strip().replaceAll("\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Clave para comparar nombres al detectar duplicados: colapsa cualquier
     * espacio en blanco, quita los acentos y pasa a minusculas, de modo que
     * "Preparación QA" y "Preparacion QA" cuenten como el mismo nombre.
     * <p>
     * QA observo en el 3er informe que esas dos cadenas se guardaban como dos
     * preparaciones distintas. Sirve solo para comparar: lo que se guarda es
     * siempre el texto tal como lo escribio el usuario, con sus acentos.
     * <p>
     * La enie se protege antes de quitar los diacriticos porque en un catalogo
     * botanico no es un acento sino otra letra: "caña" y "cana" son palabras
     * distintas y no deben colisionar.
     * <p>
     * Trabajo futuro: la base sigue con un UNIQUE sobre el nombre tal cual, asi
     * que esta comprobacion vive solo en la aplicacion. Blindar las
     * inserciones directas por SQL pediria un indice unico funcional sobre
     * {@code unaccent(lower(nombre))}, y con el la extension {@code unaccent}
     * de PostgreSQL en todos los entornos; quedo pendiente de decision del
     * equipo.
     */
    public static String claveComparacion(String valor) {
        if (valor == null) return null;
        String limpio = valor.strip().replaceAll("(?U)\\s+", " ");
        if (limpio.isEmpty()) return null;

        String protegida = limpio.replace("ñ", "\u0001").replace("Ñ", "\u0002");
        String sinAcentos = Normalizer.normalize(protegida, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return sinAcentos.replace("\u0001", "ñ").replace("\u0002", "Ñ")
                .toLowerCase(Locale.ROOT);
    }
}
