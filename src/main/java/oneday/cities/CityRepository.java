package oneday.cities;

import org.springframework.data.jpa.repository.JpaRepository;

interface CityRepository extends JpaRepository<City, String> {
}
