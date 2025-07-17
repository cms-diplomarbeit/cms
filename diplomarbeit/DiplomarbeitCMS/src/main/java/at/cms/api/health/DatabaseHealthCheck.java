package at.cms.api.health;

import at.cms.config.AppConfig;
import at.cms.training.db.Repository;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.SQLException;

@Liveness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return checkDatabaseConnection();
    }

    private HealthCheckResponse checkDatabaseConnection() {
        try (Connection connection = Repository.getConnection()) {
            if (connection != null && !connection.isClosed()) {
                return HealthCheckResponse.builder()
                    .name("Database connection")
                    .up()
                    .withData("mode", AppConfig.getServiceMode().toString())
                    .withData("watchDir", AppConfig.getWatchDir())
                    .build();
            } else {
                return HealthCheckResponse.builder()
                    .name("Database connection")
                    .down()
                    .build();
            }
        } catch (SQLException e) {
            return HealthCheckResponse.builder()
                .name("Database connection")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
