package oneday.consent;

import java.util.function.Consumer;

/**
 * What a module does when a person withdraws consent for a purpose: stop and delete that purpose's data.
 * Implemented by the module that owns the data (strategy pattern), run in the withdrawal's transaction.
 */
public interface ConsentWithdrawalEffect {

	ConsentPurpose purpose();

	void withdraw(String userId);

	static ConsentWithdrawalEffect of(ConsentPurpose purpose, Consumer<String> action) {
		return new ConsentWithdrawalEffect() {

			@Override
			public ConsentPurpose purpose() {
				return purpose;
			}

			@Override
			public void withdraw(String userId) {
				action.accept(userId);
			}
		};
	}
}
