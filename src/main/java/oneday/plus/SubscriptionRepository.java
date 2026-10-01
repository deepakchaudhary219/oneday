package oneday.plus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SubscriptionRepository extends JpaRepository<Subscription, String> {

	Optional<Subscription> findByProviderAndProviderSubscriptionId(String provider, String providerSubscriptionId);

	List<Subscription> findByUserIdOrderByCreatedAtDesc(String userId);
}
