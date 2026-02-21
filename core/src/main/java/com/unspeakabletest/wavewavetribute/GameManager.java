package com.unspeakabletest.wavewavetribute;

import com.unspeakabletest.wavewavetribute.enums.Difficulty;
import com.unspeakabletest.wavewavetribute.enums.GameMode;
import com.unspeakabletest.wavewavetribute.enums.GameType;

public class GameManager {
    private static GameManager instance;

    private GameType gameType;
    private GameMode gameMode;
    private Difficulty difficulty;

    // Add GameState enum locally or separate if needed
    public enum GameState {
        MENU,
        RUNNING,
        PAUSED,
        GAME_OVER
    }

    private GameState gameState;

    private GameManager() {
        // Defaults
        resetToDefaults();
    }

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public void resetToDefaults() {
        gameType = GameType.LEGACY;
        gameMode = GameMode.INFINITY;
        difficulty = Difficulty.WAVE;
        gameState = GameState.MENU;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }
}
