package com.nimokids.exception;

/** Topic is inactive or does not have the minimum number of playable questions (BR-001, BR-002). */
public class TopicNotPlayableException extends BusinessException {

    public TopicNotPlayableException() {
        super(ErrorCode.TOPIC_NOT_PLAYABLE);
    }
}
