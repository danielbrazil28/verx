CREATE TABLE consolidado_diario (
    comerciante_id         VARCHAR(64) NOT NULL,
    data                   DATE        NOT NULL,
    total_credito_centavos BIGINT      NOT NULL DEFAULT 0,
    total_debito_centavos  BIGINT      NOT NULL DEFAULT 0,
    atualizado_em          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (comerciante_id, data)
);

CREATE TABLE eventos_processados (
    event_id      UUID PRIMARY KEY,
    processado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
