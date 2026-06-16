package chaoxing.autosign.repository;

import chaoxing.autosign.entity.SignLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SignLogRepository extends JpaRepository<SignLog, Long> {

    Page<SignLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    void deleteByUserId(Long userId);

    Optional<SignLog> findByIdAndUserId(Long id, Long userId);
}
