package com.unspeakabletest.wavewavetribute;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public interface LevelGenerator {
    /**
     * Updates the level logic (e.g. generating new obstacles, managing animations).
     * 
     * @param delta   Time since last frame
     * @param cameraX Current camera X position
     */
    void update(float delta, float cameraX);

    /**
     * Renders the base background elements (e.g. Colored Band).
     * 
     * @param shapeRenderer ShapeRenderer instance
     * @param camera        Camera instance for culling
     */
    void renderLevelBase(ShapeRenderer shapeRenderer, OrthographicCamera camera);

    /**
     * Renders the mask elements (e.g. White Bars) that hide infinite obstacles.
     * 
     * @param shapeRenderer ShapeRenderer instance
     * @param camera        Camera instance for culling
     */
    void renderLevelMask(ShapeRenderer shapeRenderer, OrthographicCamera camera);

    /**
     * Renders the foreground obstacles.
     * 
     * @param shapeRenderer ShapeRenderer instance
     * @param camera        Camera instance for culling
     */
    void renderObstacles(ShapeRenderer shapeRenderer, OrthographicCamera camera);

    /**
     * Checks if the player position collides with any obstacles.
     * 
     * @param playerPosition The player's current position (head).
     * @return true if collision detected.
     */
    boolean checkCollision(com.badlogic.gdx.math.Vector2 playerPosition);
}
