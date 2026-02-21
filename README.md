# WaveWaveTribute | Playable Prototype

**Status:** Playable Prototype / Work in Progress

## Overview
**WaveWaveTribute** is a fast-paced, 2D endless runner arcade game built for Android and Desktop. It features an infinite, procedurally generated obstacle course powered by noise algorithms, delivering an intense and highly responsive pixel-perfect collision experience.

[![Wave Wave Tribute (Original from Thomas Janson)]](https://www.youtube.com/watch?v=iGbK1G3LYAc)

## Tech Stack
- **Language:** Java
- **Framework:** [libGDX](https://libgdx.com/)
- **Core Mechanics:** Procedural generation (FastNoise), Custom vector rendering (`ShapeRenderer`), Real-time high-precision collision detection.
- **Platforms:** Android, Desktop (LWJGL3)
- **Build System:** Gradle

## AI-Assisted Workflow 🤖
This project was developed leveraging **Vibe Coding** methodologies and advanced **LLMs** (including Google's Gemini models via the Antigravity IDE). By using AI-assisted pair programming, the development process was significantly accelerated—enabling rapid iteration on the game's architecture, mathematical generation logic (like procedural noise generation), and collision optimizations.

---

## Generator Info

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.
- `android`: Android mobile platform. Needs Android SDK.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `android:lint`: performs Android project validation.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.
