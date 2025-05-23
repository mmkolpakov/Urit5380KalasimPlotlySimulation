# Имитационное моделирование гематологического анализатора URIT-5380 с Kalasim и Plotly.kt

## Оглавление

- [Введение](#введение)
- [Описание анализатора URIT-5380](#описание-анализатора-urit-5380)
- [Цели проекта](#цели-проекта)
- [Структура проекта](#структура-проекта)
- [Реализация симуляционной модели](#реализация-симуляционной-модели)
- [Сбор данных и мониторинг](#сбор-данных-и-мониторинг)
- [Визуализация результатов](#визуализация-результатов)
- [Результаты и выводы](#результаты-и-выводы)
- [Установка и запуск](#установка-и-запуск)
- [Направления дальнейшей работы](#направления-дальнейшей-работы)
- [Зависимости](#зависимости)
- [Лицензия](#лицензия)

## Введение

В рамках данного проекта разработана модель имитационного моделирования работы гематологического анализатора URIT-5380 с использованием библиотеки Kalasim (Kotlin) для дискретно-событийного моделирования. Визуализация результатов симуляции выполнена при помощи мультиплатформенной библиотеки Plotly.kt.

Проект позволяет моделировать работу лабораторного анализатора в различных условиях, оценивать его производительность, анализировать времена обработки образцов и предсказывать возможные узкие места в лабораторных процессах.

## Описание анализатора URIT-5380

URIT-5380 — это полностью автоматизированный гематологический анализатор типа **5-Part Differential (5-Diff)**, предназначенный для количественного определения и качественной оценки форменных элементов крови: эритроцитов (RBC), лейкоцитов (WBC), тромбоцитов (PLT), измерения концентрации гемоглобина (HGB), а также для дифференциации лейкоцитов на 5 субпопуляций и расчета множества других гематологических параметров. Данный прибор используется в лаборатории МФТИ.

**Ключевые возможности и характеристики URIT-5380:**

*   Анализ до **60 образцов в час**, подходит для средних и крупных лабораторий.
*   Оснащен **автоподатчиком (автозагрузчиком)** на **50 пробирок** (5 штативов по 10 ячеек), что минимизирует ручную работу и время анализа. Возможна работа в ручном режиме подачи проб (STAT-порт).
*   Требуется всего **20 мкл** цельной венозной крови (с антикоагулянтом) или предварительно разведенной капиллярной крови.
*   **Технологии измерения:**
   *   **Дифференцировка WBC (5-Diff):** Метод **лазерной проточной цитометрии** с использованием полупроводникового лазера, химического окрашивания и анализа 4-углового светорассеяния (0°, 10°, 90°, 90° деполяризованное).
   *   **Подсчет RBC, PLT, общего WBC:** **Импедансный метод** (кондуктометрия, метод Культера).
   *   **Измерение HGB:** **Бесцианидный фотометрический метод** (измерение абсорбции при 540 нм).
*   Определяет около **40 параметров**, включая полную лейкоцитарную формулу, эритроцитарные и тромбоцитарные индексы, параметры ретикулоцитов (в режиме RET), флаги наличия атипичных клеток (ALY, LIC, NRBC).
*   **Гибкие режимы работы:**
   *   `CBC`: Общий анализ крови (подсчет WBC без дифференцировки, RBC, PLT, HGB и индексы).
   *   `CBC+5DIFF`: Общий анализ крови с полной дифференцировкой лейкоцитов на 5 популяций (нейтрофилы, лимфоциты, моноциты, эозинофилы, базофилы).
   *   `CBC+5DIFF+RRBC`: Специальный режим для анализа образцов с подозрением на наличие **резистентных к лизису эритроцитов** (флаг RRBC), которые могут ложно завышать количество лейкоцитов в стандартном режиме.
   *   `RET`: Подсчет **ретикулоцитов** (абсолютное и относительное количество, фракция незрелых ретикулоцитов - IRF). Требует предварительной инкубации образца крови со специальным красителем (например, метиленовым синим) в течение ~15 минут.
*   Контролируется **внешним компьютером** с программным обеспечением на базе ОС Windows, позволяющим управлять анализами, результатами пациентов, настройками, автоочисткой.
*   Поддерживает подключение к **Лабораторной Информационной Системе (ЛИС)**.
*   Объем памяти рассчитан на хранение более **200 000 результатов** анализов, включая скаттерограммы и гистограммы.
*   Генерирует **2 гистограммы** (распределение RBC и PLT по объему) и **2 скаттерограммы** (для дифференцировки WBC).

## Основные параметры и характеристики анализатора URIT-5380

| Параметр                           | Значение / Описание                                                                                                                                                                                   |
|:-----------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Тип анализатора**                | Автоматический гематологический, 5-Part Diff                                                                                                                                                          |
| **Производительность**             | 60 проб в час (в ручном режиме или с автоподатчиком)                                                                                                                                                  |
| **Принципы измерения**             |                                                                                                                                                                                                       |
|     Дифференцировка WBC (WOC)      | Проточная цитометрия (полупроводниковый лазер, 4-угловое светорассеяние, хим. окрашивание)                                                                                                            |
|     RBC, PLT, Общий WBC (WIC)      | Импедансный метод (метод Культера)                                                                                                                                                                    |
|     HGB                            | Фотометрический метод (бесцианидный, LED 540 нм)                                                                                                                                                      |
| **Режимы работы**                  | `CBC`, `CBC+5DIFF`, `CBC+5DIFF+RRBC`, `RET`                                                                                                                                                           |
| **Объем пробы**                    | 20 мкл (цельная кровь или предварительно разведенная)                                                                                                                                                 |
| **Автоподатчик**                   | Да, емкость 50 пробирок (5 штативов по 10)                                                                                                                                                            |
| **Дифференцировка WBC**            | На 5 популяций: Нейтрофилы (NEU), Лимфоциты (LYM), Моноциты (MON), Эозинофилы (EOS), Базофилы (BASO) (абсолютные # и относительные % значения)                                                        |
| **Измеряемые/Расчетные параметры** | Около 40: WBC, LYM(#,%), MON(#,%), NEU(#,%), EOS(#,%), BASO(#,%), RBC, HGB, HCT, MCV, MCH, MCHC, RDW(sd, cv), PLT, MPV, PDW, PCT, P-LCR, P-LCC, RET(#,%), IRF(%), ALY(#,%), LIC(#,%), NRBC(#,%) и др. |
| **Графики**                        | 2 скаттерограммы (DIFF, BASO), 2 гистограммы (RBC, PLT)                                                                                                                                               |
| **Система дозирования реагентов**  | Высокоточные шприцы (Syringe module)                                                                                                                                                                  |
| **Реагенты (основные)**            | Дилюент (Diluent), Лизирующий реагент (Lyse), Обтекающая жидкость (Sheath Fluid), Промывающий раствор (Detergent). Отдельный краситель для режима RET.                                                |
| **Обработка флага RRBC**           | Поддерживается; при появлении флага RRBC (Resistant RBC) рекомендуется перезапуск анализа в режиме `CBC+5DIFF+RRBC` для корректного подсчета WBC.                                                     |
| **Подсчет ретикулоцитов**          | Поддерживается (режим `RET`), требует отдельной пробоподготовки (инкубация с красителем). Определяет RET(#,%), IRF(%).                                                                                |
| **Объем памяти**                   | > 200 000 результатов (включая графики)                                                                                                                                                               |
| **Управление**                     | Внешний компьютер (ПО на базе Windows)                                                                                                                                                                |
| **Подключение к ЛИС**              | Поддерживается                                                                                                                                                                                        |
| **Система отходов**                | Отдельный контейнер для сбора жидких отходов (Waste container)                                                                                                                                        |

## Цели проекта

Основная цель данного проекта — создать симуляционную модель анализатора URIT-5380 для:

1. **Оценки производительности** при заданном потоке образцов разных типов
2. **Анализа времени обработки образцов** (Turnaround Time - TAT):
    * Общего времени от поступления до выдачи результата
    * Времени ожидания в очереди
    * Времени непосредственного анализа
3. **Изучения динамики ресурсов**:
    * Расхода различных реагентов
    * Накопления отходов
    * Загрузки анализатора
4. **Моделирования специфических процессов**:
    * Обработки флага RRBC (повторный анализ)
    * Инкубации для режима RET
    * Технического обслуживания
5. **Визуализации ключевых метрик**:
    * Временных рядов состояний системы
    * Статистических распределений параметров
    * Взаимосвязей между различными показателями

## Структура проекта

Проект организован стандартным образом. Структура файлов:

```
Urit5380Simulation/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   ├── space/
│   │   │   │   ├── kscience/
│   │   │   │   │   ├── Urit5380Simulation.kt    # Основная логика симуляции
│   │   │   │   │   ├── Urit5380Visualization.kt # Компоненты визуализации
│   │   │   │   │   └── Main.kt                  # Точка входа
│   │   ├── resources/                           # Ресурсы (пока нету)
│   ├── test/
│   │   ├── kotlin/                              # Тесты (пока нету)
│   │   └── resources/
├── build.gradle.kts                             # Конфигурация сборки и зависимости
└── settings.gradle.kts                          # Настройки проекта
```

Ключевые классы проекта:

* `Urit5380Simulation` — основной класс симуляции, наследующийся от `Environment` Kalasim
* `BloodSample` — класс образца крови, наследующийся от `Component` Kalasim
* `AnalyzerController` — управляет очередью образцов и инициирует анализ
* `MaintenanceTechnician` — имитирует обслуживание анализатора
* `VisualizationDataStore` — собирает данные для последующей визуализации
* `Urit5380Visualizer` — создает графики через Plotly.kt

## Реализация симуляционной модели

Модель построена с использованием библиотеки Kalasim на языке Kotlin. Основные компоненты модели:

### Окружение симуляции

```kotlin
class Urit5380Simulation(enableTracing: Boolean = true)
    : Environment(enableComponentLogger = enableTracing, tickDurationUnit = DurationUnit.SECONDS) {
    // Компоненты, ресурсы, параметры симуляции
}
```

Класс `Environment` управляет модельным временем и событиями, обеспечивает основу для дискретно-событийного моделирования.

### Моделирование образца крови

Ключевой компонент системы — образец крови, реализованный как класс `BloodSample`:

```kotlin
class BloodSample(
    val sampleId: String,
    val requestedMode: AnalysisMode,
    val isRerun: Boolean = false
) : Component(name = sampleId) {
    
    // Статистика времени обработки
    var queueEntryTime: Double = 0.0
    var analysisStartTime: Double = 0.0
    var analysisEndTime: Double = 0.0
    
    // Логика процесса обработки образца
    override fun process(): Sequence<Component> = sequence {
        // Имитация жизненного цикла образца
        // от поступления до завершения анализа
    }
}
```

Логика `process()`:

* Поступление в систему и добавление в очередь образца
* Инкубацию (для режима `RET`) с использованием оператора `hold`
* Запрос анализатора через оператор `request`
* Потребление реагентов в зависимости от режима анализа
* Симуляцию процесса анализа (задержка с нормальным распределением)
* Проверку на флаг RRBC и создание нового образца при необходимости
* Освобождение анализатора и запись статистики

### Ресурсы и очереди

Система использует различные типы ресурсов Kalasim:

* **`Resource`** для анализатора:
  ```kotlin
  val analyzerResource = dependency(qualifier = ANALYZER_RESOURCE) {
      Resource("URIT-5380", capacity = 1)
  }
  ```

* **`DepletableResource`** для реагентов и отходов:
  ```kotlin
  val reagentDiluent = dependency(qualifier = REAGENT_DILUENT) {
      DepletableResource("Diluent", AnalyzerConfig.DILUENT_CAPACITY, AnalyzerConfig.DILUENT_CAPACITY)
  }
  ```

* **`ComponentQueue`** для очереди образцов:
  ```kotlin
  val analysisQueue = dependency(qualifier = ANALYSIS_QUEUE) {
      ComponentQueue(
          "AutoloaderQueue",
          capacity = AnalyzerConfig.AUTOLOADER_CAPACITY,
          q = PriorityQueue(compareBy<CQElement<BloodSample>> { it.priority }.thenBy { it.enterTime })
      )
  }
  ```

### Состояния и контроллеры

Модель отслеживает состояния системы и реагирует на их изменения:

* **`State<AnalyzerState>`** для отслеживания состояния анализатора:
  ```kotlin
  val analyzerState = dependency(qualifier = ANALYZER_STATE) {
      State(AnalyzerState.IDLE, "Analyzer Status")
  }
  ```

* **`AnalyzerController`** для управления очередью:
  ```kotlin
  class AnalyzerController : Component("AnalyzerController") {
      override fun process(): Sequence<Component> = sequence {
          while(true) {
              // Логика выбора следующего образца из очереди
              // и инициирования его обработки
          }
      }
  }
  ```

* **`MaintenanceTechnician`** для обслуживания оборудования:
  ```kotlin
  class MaintenanceTechnician : Component("MaintenanceTechnician") {
      override fun process(): Sequence<Component> = sequence {
          while(true) {
              // Логика обслуживания анализатора
              // (замена реагентов, утилизация отходов)
          }
      }
  }
  ```

### Генерация образцов

Для симуляции потока входящих образцов используется `ComponentGenerator`:

```kotlin
ComponentGenerator(iat = normal(AnalyzerConfig.SAMPLE_ARRIVAL_MEAN, AnalyzerConfig.SAMPLE_ARRIVAL_SD)) { idx ->
    val sampleId = "Sample_${now.toTickTime().value.toInt()}_$idx"
    val mode = modeDist()  // Выбор режима по вероятностному распределению
    val newSample = BloodSample(sampleId, mode, false)
    
    // Добавление образца в систему
}
```

## Сбор данных и мониторинг

Для сбора и анализа данных о работе системы используются мониторы Kalasim и специальный класс для визуализации:

### Мониторы Kalasim

```kotlin
val turnaroundTimeMonitor = dependency(qualifier = TURNAROUND_TIME_MONITOR) {
    NumericStatisticMonitor("Sample TAT (sec)")
}

val queueLengthMonitor = dependency(qualifier = QUEUE_LENGTH_MONITOR) {
    NumericStatisticMonitor("Queue Length")
}
```

### Хранилище данных для визуализации

```kotlin
class VisualizationDataStore : SimulationDataCollector {
    val samples = mutableListOf<SampleData>()
    val stateChanges = mutableListOf<Pair<Double, AnalyzerState>>()
    val reagentLevels = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
    val wasteLevels = mutableListOf<Pair<Double, Double>>()
    val queueLengths = mutableListOf<Pair<Double, Int>>()
    val maintenanceEvents = mutableListOf<Triple<Double, Double, String>>()
    
    // Методы для добавления данных
}
```

Данные собираются в процессе симуляции и используются для создания интерактивных визуализаций.

## Визуализация результатов

Для визуализации результатов симуляции используется библиотека Plotly.kt. Класс `Urit5380Visualizer` принимает собранные данные и создает набор графиков.

### Основные типы визуализаций

1. **Временная шкала состояний анализатора**

```kotlin
fun createAnalyzerStateTimeline(): Plot {
    return Plotly.plot {
        scatter {
            x.set(timePoints)
            y.set(states)
            mode = ScatterMode.lines
            line {
                shape = LineShape.hv  // Ступенчатая линия
                width = 2.0
                color(T10.BLUE)
            }
        }
        layout {
            title = "Analyzer State Timeline"
            // Настройки осей и отображения
        }
    }
}
```

2. **Разбивка времени обработки (Box Plot)**

```kotlin
fun createSampleTurnaroundTimeBoxPlot(): Plot {
    return Plotly.plot {
        box {
            y.set(waitTimes)
            name = "Wait Time"
            boxmean = BoxMean.`true`
            marker { color(T10.BLUE) }
        }
        box {
            y.set(processTimes)
            name = "Process Time"
            boxmean = BoxMean.`true`
            marker { color(T10.ORANGE) }
        }
        // Настройки отображения
    }
}
```

3. **Уровни реагентов и отходов**

```kotlin
fun createReagentLevelsChart(): Plot {
    return Plotly.plot {
        // Для каждого реагента
        scatter {
            x.set(timePoints)
            y.set(levelValues)
            mode = ScatterMode.lines
            name = reagentName
        }
        
        // Для отходов
        scatter {
            x.set(wasteTimePoints)
            y.set(wasteLevels)
            mode = ScatterMode.lines
            name = "Waste"
            line {
                color(T10.RED)
                dash = Dash.dash
            }
        }
    }
}
```

4. **Длина очереди**

```kotlin
fun createQueueLengthChart(): Plot {
    return Plotly.plot {
        scatter {
            x.set(timePoints)
            y.set(queueLengths)
            mode = ScatterMode.lines
            line {
                shape = LineShape.hv
                color(T10.GREEN)
                width = 2.0
            }
            fill = FillType.tozeroy
        }
    }
}
```

### Сборка HTML-отчета

Все созданные графики собираются в единый HTML-отчет с использованием Bootstrap:

```kotlin
fun saveAllPlotsToHtml(filepath: String, simulationDuration: String = "N/A") {
    // Генерация всех графиков
    val statePlot = createAnalyzerStateTimeline()
    val tatBoxPlot = createSampleTurnaroundTimeBoxPlot()
    // ... другие графики
    
    // Создание HTML страницы
    val page = Plotly.page(
        cdnPlotlyHeader,
        cdnBootstrap,
        title = "URIT-5380 Simulation Results"
    ) { renderer ->
        div("container-fluid mt-3") {
            h1("mb-3") { +"URIT-5380 Simulation Visualization Report" }
            h2("mb-4 text-muted") { +"Simulation Duration: $simulationDuration" }
            
            // Добавление графиков в HTML с разметкой Bootstrap
            div("row align-items-stretch mb-4") {
                div("col-lg-7 mb-3 mb-lg-0") {
                    div("card h-100") {
                        div("card-header") { h4("card-title mb-0") { +"Analyzer State Timeline" } }
                        div("card-body") {
                            plot(statePlot, renderer = renderer)
                        }
                    }
                }
                // ... другие графики
            }
        }
    }
    
    // Сохранение HTML в файл
    page.makeFile(Paths.get(filepath).toAbsolutePath(), show = false)
}
```

## Результаты и выводы

Симуляция URIT-5380 при типичных параметрах (поток ~30 проб/час, длительность 4 часа) позволила сделать следующие выводы:

1. **Производительность анализатора:**
    * Загрузка составляет около 50% при заданных типичных параметрах потока образцов
    * Пропускная способность соответствует заявленным 60 пробам в час
    * Система не перегружена, анализатор проводит достаточно времени в состоянии IDLE

2. **Времена обработки образцов:**
    * Среднее время обработки (TAT) составляет ~60 секунд
    * Время ожидания в очереди минимально (в среднем <10 секунд)
    * Время анализа соответствует заданным параметрам (нормальное распределение с μ=60с, σ=5с)

3. **Работа с особыми случаями:**
    * Механизм обработки флага RRBC работает корректно
    * Образцы с флагом RRBC проходят повторный анализ
    * Образцы RET корректно проходят стадию инкубации

4. **Ресурсы и обслуживание:**
    * Расход реагентов соответствует ожидаемым значениям
    * Накопление отходов происходит линейно
    * За время симуляции (4 часа) не потребовалось обслуживания
    * Система мониторинга уровней реагентов и отходов функционирует корректно

5. **Визуализация:**
    * Plotly.kt позволил создать информативные графики
    * HTML-отчет обеспечил представление всех ключевых аспектов работы анализатора
    * Визуализация подтверждает выводы, сделанные на основе текстовой статистики Kalasim

## Установка и запуск

### Предварительные требования

* JDK 17 или выше
* Kotlin 2.1.10 или выше
* Gradle 8.0+ (включен в проект через wrapper)

### Шаги по установке

1. **Клонирование репозитория:**
   ```bash
   git clone https://github.com/yourusername/Urit5380Simulation.git
   cd Urit5380Simulation
   ```

2. **Сборка проекта:**
   ```bash
   ./gradlew build
   ```

   Для Windows:
   ```bash
   gradlew.bat build
   ```

3. **Запуск симуляции:**
   ```bash
   ./gradlew run
   ```

   Для Windows:
   ```bash
   gradlew.bat run
   ```

### Пример использования кода

```kotlin
// Создание и запуск симуляции
val sim = Urit5380Simulation(enableTracing = true)
sim.run(4.hours)
sim.printStatistics()

// Создание визуализации
val visualizer = Urit5380Visualizer(sim.getVisualizationDataStore())
visualizer.saveAllPlotsToHtml("urit5380_simulation_results.html", "4h")
```

После завершения:
1. Текстовые результаты будут выведены в консоль
2. HTML-отчет с графиками будет создан в корне проекта (или по указанному пути)
3. Откройте HTML-файл в любом веб-браузере для просмотра результатов

## Направления дальнейшей работы

Проект имеет потенциал для дальнейшего развития в следующих направлениях:

1. **Улучшение параметризации:**
    * Проверка корректности модели при увеличении времени симуляции
    * Создание системы конфигурационных файлов
    * Разработка интерфейса для настройки параметров симуляции
    * Поддержка пакетного запуска нескольких сценариев

2. **Расширение аналитических возможностей:**
    * Статистический анализ результатов нескольких прогонов
    * Анализ чувствительности к изменению параметров
    * Оптимизация расписания работы лаборатории

## Зависимости

Проект использует следующие библиотеки:

* **[Kalasim](https://github.com/holgerbrandl/kalasim)** (1.0.4): Библиотека для дискретно-событийного моделирования
* **[Plotly.kt](https://github.com/SciProgCentre/plotly.kt)** (0.5.0): Библиотека для построения графиков
* **[Kotlinx.html](https://github.com/Kotlin/kotlinx.html)**: Библиотека для программного создания HTML
* **[Koin](https://insert-koin.io/)**: Фреймворк для внедрения зависимостей
* **[SLF4J](https://www.slf4j.org/)** (slf4j-simple): Для логирования

Все зависимости настроены через Gradle и автоматически загружаются при сборке проекта.

## Лицензия

Этот проект распространяется под лицензией MIT.

Copyright © 2025 Maxim Kolpakov, MIPT FPMI.