package com.nimokids.repository;

import com.nimokids.entity.QuestionSticker;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionStickerRepository extends JpaRepository<QuestionSticker, UUID> {

    List<QuestionSticker> findByQuestionIdIn(Collection<UUID> questionIds);
}
