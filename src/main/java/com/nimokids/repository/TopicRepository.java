package com.nimokids.repository;

import com.nimokids.entity.Topic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findByCode(String code);

    Optional<Topic> findBySlug(String slug);

    List<Topic> findByActiveTrueOrderByDisplayOrderAsc();
}
