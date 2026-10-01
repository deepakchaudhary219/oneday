package oneday.platform;

import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Primary/replica routing, active only when {@code oneday.datasource.replica.url} is set (MySQL replicas
 * behind the primary). The connection is fetched lazily, after the transaction's read-only flag is known, and
 * goes to the replica only for {@link ReplicaReads} work inside a read-only transaction.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "oneday.datasource.replica.url")
public class ReadReplicaRouting {

	public enum Target {
		PRIMARY, REPLICA
	}

	@Bean
	@ConfigurationProperties("spring.datasource.hikari")
	HikariDataSource primaryDataSource(DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
	}

	@Bean
	@ConfigurationProperties("oneday.datasource.replica")
	HikariDataSource replicaDataSource() {
		HikariDataSource replica = new HikariDataSource();
		replica.setReadOnly(true);
		replica.setPoolName("replica");
		return replica;
	}

	@Bean
	@Primary
	DataSource dataSource(HikariDataSource primaryDataSource, HikariDataSource replicaDataSource) {
		Router router = new Router();
		router.setTargetDataSources(Map.of(Target.PRIMARY, primaryDataSource, Target.REPLICA, replicaDataSource));
		router.setDefaultTargetDataSource(primaryDataSource);
		router.afterPropertiesSet();
		return new LazyConnectionDataSourceProxy(router);
	}

	static final class Router extends AbstractRoutingDataSource {

		@Override
		protected Object determineCurrentLookupKey() {
			return currentTarget();
		}
	}

	static Target currentTarget() {
		return ReplicaReads.allowed() && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
				? Target.REPLICA : Target.PRIMARY;
	}
}
