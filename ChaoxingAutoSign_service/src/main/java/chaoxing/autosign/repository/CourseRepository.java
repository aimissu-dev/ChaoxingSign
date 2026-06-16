package chaoxing.autosign.repository;

import chaoxing.autosign.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByUserId(Long userId);

    List<Course> findByUserIdAndStatus(Long userId, Integer status);

    Optional<Course> findByUserIdAndCourseId(Long userId, String courseId);

    void deleteByUserIdAndCourseId(Long userId, String courseId);

    List<Course> findByUserIdAndDayOfWeek(Long userId, Integer dayOfWeek);
}
