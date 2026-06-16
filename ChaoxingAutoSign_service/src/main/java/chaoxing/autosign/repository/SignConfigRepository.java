package chaoxing.autosign.repository;

import chaoxing.autosign.entity.SignConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SignConfigRepository extends JpaRepository<SignConfig, Long> {

    List<SignConfig> findByUserId(Long userId);

    Optional<SignConfig> findByUserIdAndCourseName(Long userId, String courseName);

    void deleteByUserIdAndCourseName(Long userId, String courseName);
}
