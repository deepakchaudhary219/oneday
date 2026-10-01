package oneday.figures;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PublicFigureRepository extends JpaRepository<PublicFigure, String> {

	Optional<PublicFigure> findByHandle(String handle);

	List<PublicFigure> findByStatusOrderByAppliedAt(PublicFigure.Status status);
}

interface FigureFollowRepository extends JpaRepository<FigureFollow, FigureFollow.Key> {

	@Query("select f from FigureFollow f where f.key.followerId = :followerId order by f.createdAt desc")
	List<FigureFollow> findByFollower(@Param("followerId") String followerId);

	@Query("select count(f) from FigureFollow f where f.key.figureId = :figureId")
	long countFollowers(@Param("figureId") String figureId);

	@Modifying
	@Query("delete from FigureFollow f where (f.key.followerId = :a and f.key.figureId = :b) "
			+ "or (f.key.followerId = :b and f.key.figureId = :a)")
	void deleteBetween(@Param("a") String a, @Param("b") String b);

	@Modifying
	@Query("delete from FigureFollow f where f.key.followerId = :userId or f.key.figureId = :userId")
	void deleteInvolving(@Param("userId") String userId);
}
