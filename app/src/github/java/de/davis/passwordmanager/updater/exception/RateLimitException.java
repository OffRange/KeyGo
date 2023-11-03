package de.davis.passwordmanager.updater.exception;

import org.kohsuke.github.HttpException;
import org.kohsuke.github.connector.GitHubConnectorResponse;

import java.io.Serial;
import java.util.Objects;

public class RateLimitException extends HttpException {

    @Serial
    private static final long serialVersionUID = -4947605397389299267L;

    private final long reset;

    public RateLimitException(GitHubConnectorResponse connectorResponse) {
        super(connectorResponse);
        reset = Long.parseLong(Objects.requireNonNull(connectorResponse.header("X-Ratelimit-Reset")));
    }

    public long getReset() {
        return reset;
    }
}
