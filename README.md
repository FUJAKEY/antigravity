# Antigravity Horror Mod

**Antigravity** is a highly advanced psychological horror mod for Minecraft 1.16.5 (Forge).
It introduces deep sanity mechanics, physiological horror elements, and a persistent stalking entity known as "The Hollow".

**Disclaimer**: This mod contains loud noises, jumpscares, and flashing lights. Play at your own risk.

---

## 🇺🇸 English Description

### Features

#### 🧠 Sanity System
The core of the mod is the Sanity System. Every player starts with 100% sanity, which decays based on:
- **Darkness**: Staying in light level < 7 drains sanity.
- **Depth**: Being deep underground (Y < 30) accelerates the decay.
- **Monsters**: Being near hostile mobs induces panic.
- **Moon Phase**: Full moons are especially dangerous.

**Sanity Stages:**
1.  **Anxiety (70-100%)**: You hear occasional whispers.
2.  **Paranoia (50-70%)**: Movement slows down. You hear phantom footsteps.
3.  **Panic (30-50%)**: Vision blurs. Hallucinations appear in chat.
4.  **Insanity (<30%)**: You cannot sleep. Real damage is taken from fear. "The Hollow" begins to hunt.

#### 👁️ The Hollow
"The Hollow" is a tall, distorted entity that stalks players with low sanity.
- It hides behind trees and walls.
- It destroys light sources (torches/lanterns) to create darkness.
- It will teleport behind you if you look away.
- **DO NOT STARE**: Looking at it for too long provokes an instant attack.

### Configuration
The mod is highly configurable (coming soon to config file):
- Adjust decay rates.
- Toggle jumpscares.
- Set "Safe Zones" (e.g. near beacons).

### Installation
1.  Install Minecraft Forge 1.16.5 (Recommended build).
2.  Drop `antigravity-1.0.jar` into your `mods` folder.
3.  Launch the game.

---

## 🇷🇺 Русское Описание

### Особенности

#### 🧠 Система Рассудка
Основа мода — Система Рассудка. Каждый игрок начинает с 100% рассудка, который падает в зависимости от:
- **Темноты**: Нахождение в уровне света < 7 снижает рассудок.
- **Глубины**: Нахождение глубоко под землей (Y < 30) ускоряет падение.
- **Монстров**: Близость к враждебным мобам вызывает панику.
- **Фазы Луны**: Полнолуние особенно опасно.

**Стадии Безумия:**
1.  **Тревога (70-100%)**: Вы слышите редкий шепот.
2.  **Паранойя (50-70%)**: Движение замедляется. Вы слышите фантомные шаги.
3.  **Паника (30-50%)**: Зрение размывается. В чате появляются галлюцинации.
4.  **Безумие (<30%)**: Вы не можете спать. Страх наносит реальный урон. "Пустой" (The Hollow) начинает охоту.

#### 👁️ Пустой (The Hollow)
"Пустой" — это высокое, искаженное существо, которое преследует игроков с низким рассудком.
- Он прячется за деревьями и стенами.
- Он уничтожает источники света (факелы/фонари), создавая темноту.
- Он телепортируется вам за спину, если вы отвернетесь.
- **НЕ СМОТРИТЕ**: Долгий взгляд на него провоцирует мгновенную атаку.

### Конфигурация
Мод гибко настраивается (в будущем конфиг-файле):
- Настройка скорости падения рассудка.
- Включение/выключение скримеров.
- Установка "Безопасных Зон" (например, рядом с маяками).

### Установка
1.  Установите Minecraft Forge 1.16.5 (Рекомендуемая версия).
2.  Переместите `antigravity-1.0.jar` в папку `mods`.
3.  Запустите игру.

---

## Development / Разработка

### Building / Сборка
```bash
./gradlew build
```

### Artifacts / Артефакты
Builds are available in the GitHub Actions / Сборки доступны в GitHub Actions.

**License**: All Rights Reserved / Все права защищены.
