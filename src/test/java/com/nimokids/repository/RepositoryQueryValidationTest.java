package com.nimokids.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts only the JPA layer WITHOUT a database connection. Spring Data validates every @Query and derived query
 * against the entity metamodel at startup, so a broken JPQL string or property name fails this test.
 * It cannot validate SQL semantics or the Flyway schema: that needs a real PostgreSQL.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/unused",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RepositoryQueryValidationTest {

    @Autowired
    private GameQuestionRepository questionRepository;

    @Autowired
    private GameSessionRepository sessionRepository;

    @Autowired
    private AnonymousPlayerRepository playerRepository;

    @Autowired
    private PlayerStickerRepository playerStickerRepository;

    @Autowired
    private QuestionStickerRepository questionStickerRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private GameModeRepository gameModeRepository;

    @Test
    void allRepositoryQueriesAreValidAgainstTheEntityModel() {
        assertThat(questionRepository).isNotNull();
        assertThat(sessionRepository).isNotNull();
        assertThat(playerRepository).isNotNull();
        assertThat(playerStickerRepository).isNotNull();
        assertThat(questionStickerRepository).isNotNull();
        assertThat(topicRepository).isNotNull();
        assertThat(gameModeRepository).isNotNull();
    }
}
