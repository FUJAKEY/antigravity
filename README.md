# Antigravity Horror Mod

**Antigravity** is a highly advanced psychological horror mod for Minecraft 1.16.5 (Forge).
It introduces deep sanity mechanics, physiological horror elements, gravitational anomalies, and a persistent stalking entity known as "The Hollow".

> **Disclaimer**: This mod contains loud noises, jumpscares, and flashing lights. Play at your own risk.

## 🇺🇸 English Description

### Core Features

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
- **Adaptive AI**: Uses an Acoustic Sensor to track player footsteps and block breaking.
- **Phase Shift**: Capable of "Quantum Vanishing" when observed directly, teleporting to safety.
- **Light Breaking**: Actively destroys torches and lanterns to create darkness.
- **Adaptive Defense**: Learns from damage sources and builds temporary resistance.
- **DO NOT STARE**: Looking at it for too long provokes an instant attack.

#### 🌌 Gravity Anomalies
Unstable regions of space-time that warp physics.
- **Effects**: Can reverse gravity, induce nausea, or spawn entities.
- **Management**: Use the **Anomaly Scanner** to detect nearby fields.

#### 📟 Anomaly Scanner
A high-tech tool for tracking anomalies and paranormal activity.
- **Visualization Engine**: Displays nearby anomaly vectors.
- **Battery System**: Requires charging; enters low-power mode when idle.

### Technical Architecture
The mod is built with advanced internal systems:
- **Performance Tracker**: Dynamically adjusts mod intensity based on server tick rate (TPS).
- **Config Integrity**: Automatically validates configuration values to prevent crashes.
- **Backup Manager**: Securely handles mod data persistence.

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

#### 👁️ Пустой (The Hollow)
"Пустой" — это высокое, искаженное существо, которое преследует игроков с низким рассудком.
- **Адаптивный ИИ**: Использует акустический сенсор для отслеживания шагов игрока.
- **Фазовый Сдвиг**: Способен исчезать (телепортироваться) при прямом зрительном контакте.
- **Уничтожение Света**: Ломает факелы, чтобы создать темноту.
- **Адаптивная Защита**: Получает сопротивление к типу урона, который вы используете.

#### 🌌 Гравитационные Аномалии
Нестабильные области пространства-времени.
- **Эффекты**: Могут инвертировать гравитацию, вызывать тошноту или призывать существ.
- **Обнаружение**: Используйте **Сканер Аномалий**.

#### 📟 Сканер Аномалий
Высокотехнологичное устройство для поиска аномалий.
- **Визуализация**: Показывает векторы ближайших полей.
- **Батарея**: Требует зарядки; переходит в спящий режим для экономии энергии.

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

**License**: All Rights Reserved / Все права защищены.
