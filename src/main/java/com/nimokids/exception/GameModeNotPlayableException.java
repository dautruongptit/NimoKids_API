package com.nimokids.exception;

public class GameModeNotPlayableException extends BusinessException {

    public GameModeNotPlayableException() {
        super(ErrorCode.GAME_MODE_NOT_PLAYABLE);
    }
}
