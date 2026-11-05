package br.com.fluxocaixa.lancamentos.aplicacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class HashConteudo {

    private HashConteudo() {
    }

    static String de(Object... partes) {
        StringBuilder texto = new StringBuilder();
        for (Object parte : partes) {
            texto.append(parte).append('|');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
