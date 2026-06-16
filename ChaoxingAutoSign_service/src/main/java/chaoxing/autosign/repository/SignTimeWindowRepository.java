package chaoxing.autosign.repository;

import chaoxing.autosign.entity.SignTimeWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SignTimeWindowRepository extends JpaRepository<SignTimeWindow, Long> {

    List<SignTimeWindow> findByConfigId(Long configId);

    @Modifying
    @Transactional
    void deleteByConfigId(Long configId);

    /** 根据配置 ID 列表查所有时间窗口 */
    @Query("SELECT w FROM SignTimeWindow w WHERE w.configId IN :configIds")
    List<SignTimeWindow> findByConfigIdIn(@Param("configIds") List<Long> configIds);

    /** 批量删除某个配置下的时间窗口 */
    @Modifying
    @Transactional
    @Query("DELETE FROM SignTimeWindow w WHERE w.configId = :configId")
    void deleteAllByConfigId(@Param("configId") Long configId);
}
