package oneday.platform;

import static org.assertj.core.api.Assertions.assertThat;

import oneday.platform.ReadReplicaRouting.Target;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Only opted-in work inside a read-only transaction may go to the replica. */
class ReadReplicaRoutingTest {

	@AfterEach
	void reset() {
		TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
	}

	@Test
	void writesAndUnmarkedReadsStayOnThePrimary() {
		assertThat(ReadReplicaRouting.currentTarget()).isEqualTo(Target.PRIMARY);
		TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
		assertThat(ReadReplicaRouting.currentTarget()).isEqualTo(Target.PRIMARY); // not opted in
		TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
		assertThat(ReplicaReads.run(ReadReplicaRouting::currentTarget)).isEqualTo(Target.PRIMARY); // a write
	}

	@Test
	void optedInReadOnlyWorkGoesToTheReplica() {
		TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
		assertThat(ReplicaReads.run(ReadReplicaRouting::currentTarget)).isEqualTo(Target.REPLICA);
		assertThat(ReadReplicaRouting.currentTarget()).isEqualTo(Target.PRIMARY); // scope ends with run()
	}
}
