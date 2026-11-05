CREATE TABLE lancamento (
    id               UUID PRIMARY KEY,
    comerciante_id   VARCHAR(64) NOT NULL,
    tipo             VARCHAR(7)  NOT NULL CHECK (tipo IN ('CREDITO', 'DEBITO')),
    valor_centavos   BIGINT      NOT NULL CHECK (valor_centavos > 0),
    data_competencia DATE        NOT NULL,
    estorno_de       UUID        REFERENCES lancamento (id),
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lancamento_comerciante_data ON lancamento (comerciante_id, data_competencia, id);
-- RN06: um lançamento só pode ser estornado uma vez.
CREATE UNIQUE INDEX uq_lancamento_estorno_de ON lancamento (estorno_de) WHERE estorno_de IS NOT NULL;

CREATE TABLE idempotencia (
    comerciante_id VARCHAR(64) NOT NULL,
    chave          VARCHAR(128) NOT NULL,
    hash_conteudo  CHAR(64)    NOT NULL,
    lancamento_id  UUID        NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (comerciante_id, chave)
);

CREATE TABLE outbox (
    event_id     UUID PRIMARY KEY,
    tipo         VARCHAR(64) NOT NULL,
    payload      TEXT        NOT NULL,
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    publicado_em TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pendentes ON outbox (criado_em) WHERE publicado_em IS NULL;
