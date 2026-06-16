package chaoxing.autosign.repository;

import chaoxing.autosign.entity.SignRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignRecordRepository extends JpaRepository<SignRecord, Long> {

    Page<SignRecord> findByUserIdOrderBySignTimeDesc(Long userId, Pageable pageable);

    List<SignRecord> findByUserIdAndActiveId(Long userId, String activeId);

    boolean existsByUserIdAndActiveId(Long userId, String activeId);
}
