package space.kscience

import org.kalasim.*
import org.kalasim.monitors.*
import org.koin.core.component.inject
import org.koin.core.qualifier.StringQualifier
import org.koin.core.qualifier.named
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

object AnalyzerConfig {
    // Временные параметры
    val ANALYSIS_TIME_MEAN = 60.seconds
    val ANALYSIS_TIME_SD = 5.seconds
    val SAMPLE_ARRIVAL_MEAN = 2.minutes
    val SAMPLE_ARRIVAL_SD = 10.seconds
    val RET_INCUBATION_TIME = 15.minutes

    // Объемы
    const val AUTOLOADER_CAPACITY = 50
    const val SAMPLE_VOLUME_ML = 0.02
    const val DILUENT_CAPACITY = 5000.0
    const val LYSE_CAPACITY = 1000.0
    const val SHEATH_CAPACITY = 10000.0
    const val DETERGENT_CAPACITY = 1000.0
    const val WASTE_CAPACITY = 20000.0

    // Расход реагентов
    const val DILUENT_PER_SAMPLE = 2.0
    const val LYSE_PER_DIFF_SAMPLE = 0.5
    const val SHEATH_PER_DIFF_SAMPLE = 5.0
    const val WASTE_PER_SAMPLE_BASE = SAMPLE_VOLUME_ML + DILUENT_PER_SAMPLE
    const val WASTE_PER_SAMPLE_DIFF = WASTE_PER_SAMPLE_BASE + LYSE_PER_DIFF_SAMPLE + SHEATH_PER_DIFF_SAMPLE

    // Пороговые значения
    const val REAGENT_LOW_THRESHOLD_PCT = 10.0
    const val WASTE_HIGH_THRESHOLD_PCT = 90.0

    // Вероятностные распределения
    private const val PROB_CBC = 0.1
    private const val PROB_CBC_5DIFF = 0.8
    private const val PROB_RET = 0.1
    val MODE_PROBABILITIES: Map<AnalysisMode, Double> = mapOf(
        AnalysisMode.CBC to PROB_CBC,
        AnalysisMode.CBC_5DIFF to PROB_CBC_5DIFF,
        AnalysisMode.RET to PROB_RET
    )
    const val RRBC_FLAG_PROBABILITY = 0.02

    // Времена технического обслуживания
    val TECHNICIAN_PREP_TIME = 5.minutes
    val REAGENT_REPLACE_TIME = 2.minutes
    val WASTE_DISPOSAL_TIME = 5.minutes
    val MAINTENANCE_FINISH_TIME = 2.minutes

    init {
        require(MODE_PROBABILITIES.values.sum().let { it > 0.999 && it < 1.001 }) {
            "Sum of mode probabilities must be 1.0"
        }
    }
}

enum class AnalysisMode {
    CBC, CBC_5DIFF, CBC_5DIFF_RRBC, RET
}

enum class AnalyzerState {
    IDLE, ANALYZING,
    MAINTENANCE, MAINTENANCE_REAGENT, MAINTENANCE_WASTE,
    ERROR_REAGENT, ERROR_WASTE, ERROR_RRBC
}

enum class SampleStatus {
    COMPLETED, INTERRUPTED, FAILED
}

interface SimulationDataCollector {
    fun addSample(sample: SampleData)
    fun addStateChange(time: Double, state: AnalyzerState)
    fun addReagentLevel(time: Double, name: String, level: Double)
    fun addWasteLevel(time: Double, level: Double)
    fun addQueueLength(time: Double, length: Int)
    fun addMaintenanceEvent(startTime: Double, endTime: Double, type: String)
}

// Класс для отслеживания времени
class TimeTracker {
    fun getNormalizedDuration(startTime: Double, endTime: Double): Double {
        return endTime - startTime
    }
}

// Класс для хранения сводных данных симуляции для визуализации
data class SampleData(
    val id: String,
    val mode: AnalysisMode,
    val isRerun: Boolean,
    val queueTime: Double,
    val analysisStartTime: Double,
    val analysisEndTime: Double,
    val status: SampleStatus
)

// Хранилище данных для визуализации - реализация интерфейса SimulationDataCollector
class VisualizationDataStore : SimulationDataCollector {
    val samples = mutableListOf<SampleData>()
    val stateChanges = mutableListOf<Pair<Double, AnalyzerState>>()
    val reagentLevels = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
    val wasteLevels = mutableListOf<Pair<Double, Double>>()
    val queueLengths = mutableListOf<Pair<Double, Int>>()
    val maintenanceEvents = mutableListOf<Triple<Double, Double, String>>()

    init {
        // Инициализируем списки для всех реагентов
        reagentLevels["Diluent"] = mutableListOf()
        reagentLevels["Lyse"] = mutableListOf()
        reagentLevels["Sheath"] = mutableListOf()
        reagentLevels["Detergent"] = mutableListOf()
    }

    override fun addSample(sample: SampleData) {
        samples.add(sample)
    }

    override fun addStateChange(time: Double, state: AnalyzerState) {
        stateChanges.add(Pair(time, state))
    }

    override fun addReagentLevel(time: Double, name: String, level: Double) {
        reagentLevels[name]?.add(Pair(time, level))
    }

    override fun addWasteLevel(time: Double, level: Double) {
        wasteLevels.add(Pair(time, level))
    }

    override fun addQueueLength(time: Double, length: Int) {
        queueLengths.add(Pair(time, length))
    }

    override fun addMaintenanceEvent(startTime: Double, endTime: Double, type: String) {
        maintenanceEvents.add(Triple(startTime, endTime, type))
    }
}

class BloodSample(
    val sampleId: String,
    val requestedMode: AnalysisMode,
    val isRerun: Boolean = false
) : Component(name = sampleId) {

    // Статистика времени обработки
    var queueEntryTime: Double = 0.0
    var analysisStartTime: Double = 0.0
    var analysisEndTime: Double = 0.0

    // Инъекция зависимостей
    val analyzer: Resource by inject(qualifier = ANALYZER_RESOURCE)
    val reagentDiluent: DepletableResource by inject(qualifier = REAGENT_DILUENT)
    val reagentLyse: DepletableResource by inject(qualifier = REAGENT_LYSE)
    val reagentSheath: DepletableResource by inject(qualifier = REAGENT_SHEATH)
    val wasteContainer: DepletableResource by inject(qualifier = WASTE_CONTAINER)
    val analyzerState: State<AnalyzerState> by inject(qualifier = ANALYZER_STATE)
    val analysisQueue: ComponentQueue<BloodSample> by inject(qualifier = ANALYSIS_QUEUE)
    val timeTracker: TimeTracker by inject(qualifier = TIME_TRACKER)
    val vizDataStore: SimulationDataCollector by inject(qualifier = VIZ_DATA_STORE)

    companion object {
        val completedSamples = AtomicInteger(0)
        val interruptedSamples = AtomicInteger(0)
        val errorSamples = AtomicInteger(0)
    }

    private fun handleReagentFailureSignal(reagent: DepletableResource) {
        log("ERROR: reagent ${reagent.name} depleted (sample=$sampleId)")
        get<State<Boolean>>(qualifier = NEEDS_MAINTENANCE_REAGENT).value = true
        get<MaintenanceTechnician>().activate()

        // Регистрируем время ошибки
        val errorTime = env.now.toTickTime().value
        val duration = timeTracker.getNormalizedDuration(
            queueEntryTime,
            errorTime
        )

        log("Error TAT for $sampleId: $duration (start=$queueEntryTime, error=$errorTime)")
        get<NumericStatisticMonitor>(qualifier = TURNAROUND_TIME_MONITOR_FAILED).addValue(duration)
        errorSamples.incrementAndGet()

        // Добавляем данные для визуализации
        vizDataStore.addSample(
            SampleData(
                id = sampleId,
                mode = requestedMode,
                isRerun = isRerun,
                queueTime = queueEntryTime,
                analysisStartTime = analysisStartTime,
                analysisEndTime = errorTime,
                status = SampleStatus.FAILED
            )
        )
    }

    private fun handleWasteFailureSignal() {
        log("ERROR: waste container full (sample=$sampleId)")
        get<State<Boolean>>(qualifier = NEEDS_MAINTENANCE_WASTE).value = true
        get<MaintenanceTechnician>().activate()

        // Регистрируем время ошибки
        val errorTime = env.now.toTickTime().value
        val duration = timeTracker.getNormalizedDuration(
            queueEntryTime,
            errorTime
        )

        log("Error TAT for $sampleId: $duration (start=$queueEntryTime, error=$errorTime)")
        get<NumericStatisticMonitor>(qualifier = TURNAROUND_TIME_MONITOR_FAILED).addValue(duration)
        errorSamples.incrementAndGet()

        // Добавляем данные для визуализации
        vizDataStore.addSample(
            SampleData(
                id = sampleId,
                mode = requestedMode,
                isRerun = isRerun,
                queueTime = queueEntryTime,
                analysisStartTime = analysisStartTime,
                analysisEndTime = errorTime,
                status = SampleStatus.FAILED
            )
        )
    }

    override fun process(): Sequence<Component> {
        val sample = this

        return sequence {
            // Сохраняем время добавления в очередь
            sample.queueEntryTime = env.now.toTickTime().value
            log("Starting $sampleId mode=$requestedMode, rerun=$isRerun, entryTime=$queueEntryTime")

            // Добавляем в очередь ожидания
            analysisQueue.add(sample)

            // Обновляем данные о длине очереди для визуализации
            vizDataStore.addQueueLength(env.now.toTickTime().value, analysisQueue.size)

            // Для режима RET инкубация требуется перед анализом
            if(requestedMode == AnalysisMode.RET && !isRerun) {
                hold(AnalyzerConfig.RET_INCUBATION_TIME, description="RET incubation for $sampleId")
            }

            // Запрос анализатора
            request(analyzer)
            if(failed) {
                log("Analyzer request failed => abort sample $sampleId.")
                analyzerState.value = AnalyzerState.ERROR_REAGENT

                val errorTime = env.now.toTickTime().value
                val duration = timeTracker.getNormalizedDuration(
                    sample.queueEntryTime,
                    errorTime
                )

                log("Error TAT for $sampleId: $duration (start=${sample.queueEntryTime}, error=$errorTime)")
                get<NumericStatisticMonitor>(qualifier = TURNAROUND_TIME_MONITOR_FAILED).addValue(duration)
                errorSamples.incrementAndGet()

                // Добавляем данные для визуализации
                vizDataStore.addSample(
                    SampleData(
                        id = sampleId,
                        mode = requestedMode,
                        isRerun = isRerun,
                        queueTime = queueEntryTime,
                        analysisStartTime = 0.0, // не было начато
                        analysisEndTime = errorTime,
                        status = SampleStatus.FAILED
                    )
                )

                return@sequence
            }

            // Сохраняем время начала анализа
            sample.analysisStartTime = env.now.toTickTime().value
            log("Analyzer acquired for $sampleId at ${sample.analysisStartTime}, wait time: ${sample.analysisStartTime - sample.queueEntryTime}")

            analyzerState.value = AnalyzerState.ANALYZING

            // Запрос Diluent-а
            request(reagentDiluent withQuantity AnalyzerConfig.DILUENT_PER_SAMPLE)
            if(failed) {
                handleReagentFailureSignal(reagentDiluent)
                analyzerState.value = AnalyzerState.ERROR_REAGENT
                release(analyzer)
                return@sequence
            }

            // Для режимов с дифференциацией нужны дополнительные реагенты
            if(requestedMode == AnalysisMode.CBC_5DIFF || requestedMode == AnalysisMode.CBC_5DIFF_RRBC) {
                request(reagentLyse withQuantity AnalyzerConfig.LYSE_PER_DIFF_SAMPLE)
                if(failed) {
                    handleReagentFailureSignal(reagentLyse)
                    analyzerState.value = AnalyzerState.ERROR_REAGENT
                    release(analyzer)
                    return@sequence
                }

                request(reagentSheath withQuantity AnalyzerConfig.SHEATH_PER_DIFF_SAMPLE)
                if(failed) {
                    handleReagentFailureSignal(reagentSheath)
                    analyzerState.value = AnalyzerState.ERROR_REAGENT
                    release(analyzer)
                    return@sequence
                }
            }

            // Выполнение анализа
            val analysisTime = normal(AnalyzerConfig.ANALYSIS_TIME_MEAN, AnalyzerConfig.ANALYSIS_TIME_SD).sample()
            hold(analysisTime, "Analyzing $sampleId")

            // Добавление отходов
            val wasteAmount = if(requestedMode == AnalysisMode.CBC_5DIFF || requestedMode == AnalysisMode.CBC_5DIFF_RRBC) {
                AnalyzerConfig.WASTE_PER_SAMPLE_DIFF
            } else {
                AnalyzerConfig.WASTE_PER_SAMPLE_BASE
            }
            put(wasteContainer withQuantity wasteAmount, capacityLimitMode = CapacityLimitMode.FAIL)
            if(failed) {
                handleWasteFailureSignal()
                analyzerState.value = AnalyzerState.ERROR_WASTE
                release(analyzer)
                return@sequence
            }

            // Сохраняем время завершения анализа
            sample.analysisEndTime = env.now.toTickTime().value
            log("Analysis complete for $sampleId at ${sample.analysisEndTime}, mode=$requestedMode")

            // Проверка на RRBC флаг
            val rrbcFlag = (!isRerun
                    && requestedMode == AnalysisMode.CBC_5DIFF
                    && env.random.nextDouble() < AnalyzerConfig.RRBC_FLAG_PROBABILITY)

            if(rrbcFlag) {
                log("RRBC triggered => new run for $sampleId")
                analyzerState.value = AnalyzerState.ERROR_RRBC

                // Создаем повторный образец со ссылкой на оригинал
                val rrbcSample = BloodSample(
                    sampleId = "${sampleId}_RRBC",
                    requestedMode = AnalysisMode.CBC_5DIFF_RRBC,
                    isRerun = true
                )

                // Добавляем в очередь с высоким приоритетом
                analysisQueue.add(rrbcSample, priority = Priority.IMPORTANT)
                get<AnalyzerController>().activate()

                // Регистрируем статистику прерванных образцов
                val duration = timeTracker.getNormalizedDuration(
                    sample.queueEntryTime,
                    sample.analysisEndTime
                )

                log("Interrupted TAT for $sampleId: $duration (start=${sample.queueEntryTime}, end=${sample.analysisEndTime})")
                get<NumericStatisticMonitor>(qualifier = TURNAROUND_TIME_MONITOR_INTERRUPTED).addValue(duration)
                interruptedSamples.incrementAndGet()

                // Добавляем данные для визуализации
                vizDataStore.addSample(
                    SampleData(
                        id = sampleId,
                        mode = requestedMode,
                        isRerun = isRerun,
                        queueTime = queueEntryTime,
                        analysisStartTime = analysisStartTime,
                        analysisEndTime = analysisEndTime,
                        status = SampleStatus.INTERRUPTED
                    )
                )

                release(analyzer)
                return@sequence
            } else {
                log("Sample $sampleId finished normally.")

                // Регистрируем успешное завершение
                val duration = timeTracker.getNormalizedDuration(
                    sample.queueEntryTime,
                    sample.analysisEndTime
                )

                log("Complete TAT for $sampleId: $duration (start=${sample.queueEntryTime}, end=${sample.analysisEndTime})")

                // Добавляем разбивку времени по фазам
                val waitTime = sample.analysisStartTime - sample.queueEntryTime
                val processTime = sample.analysisEndTime - sample.analysisStartTime

                log("Time breakdown for $sampleId: Wait=$waitTime, Process=$processTime, Total=$duration")

                // Обновляем мониторы статистики
                get<NumericStatisticMonitor>(qualifier = TURNAROUND_TIME_MONITOR).addValue(duration)
                get<NumericStatisticMonitor>(qualifier = WAIT_TIME_MONITOR).addValue(waitTime)
                get<NumericStatisticMonitor>(qualifier = PROCESS_TIME_MONITOR).addValue(processTime)

                // Счетчик завершенных образцов
                completedSamples.incrementAndGet()

                // Добавляем данные для визуализации
                vizDataStore.addSample(
                    SampleData(
                        id = sampleId,
                        mode = requestedMode,
                        isRerun = isRerun,
                        queueTime = queueEntryTime,
                        analysisStartTime = analysisStartTime,
                        analysisEndTime = analysisEndTime,
                        status = SampleStatus.COMPLETED
                    )
                )

                analyzerState.value = AnalyzerState.IDLE
                get<AnalyzerController>().activate()
            }

            release(analyzer)
            log("BloodSample $sampleId done.")
        }
    }
}

class AnalyzerController : Component("AnalyzerController") {
    val analyzerState: State<AnalyzerState> by inject(qualifier = ANALYZER_STATE)
    val analysisQueue: ComponentQueue<BloodSample> by inject(qualifier = ANALYSIS_QUEUE)
    val vizDataStore: SimulationDataCollector by inject(qualifier = VIZ_DATA_STORE)

    override fun process(): Sequence<Component> = sequence {
        while(true) {
            val canWork = (analysisQueue.isNotEmpty()
                    && (analyzerState.value == AnalyzerState.IDLE || analyzerState.value == AnalyzerState.ERROR_RRBC)
                    )
            if(canWork) {
                log("Controller picks next sample, queue size=${analysisQueue.size}")
                val nextSample = analysisQueue.poll()

                // Обновляем данные о длине очереди для визуализации
                vizDataStore.addQueueLength(env.now.toTickTime().value, analysisQueue.size)

                nextSample.activate()
            } else {
                log("Controller passivate, state=${analyzerState.value}, queue=${analysisQueue.size}")
                passivate()
            }
        }
    }
}

class MaintenanceTechnician : Component("MaintenanceTechnician") {
    val needsReagentMaintenance: State<Boolean> by inject(qualifier = NEEDS_MAINTENANCE_REAGENT)
    val needsWasteMaintenance: State<Boolean> by inject(qualifier = NEEDS_MAINTENANCE_WASTE)
    val analyzer: Resource by inject(qualifier = ANALYZER_RESOURCE)
    val analyzerState: State<AnalyzerState> by inject(qualifier = ANALYZER_STATE)

    val reagentDiluent: DepletableResource by inject(qualifier = REAGENT_DILUENT)
    val reagentLyse: DepletableResource by inject(qualifier = REAGENT_LYSE)
    val reagentSheath: DepletableResource by inject(qualifier = REAGENT_SHEATH)
    val reagentDetergent: DepletableResource by inject(qualifier = REAGENT_DETERGENT)
    val wasteContainer: DepletableResource by inject(qualifier = WASTE_CONTAINER)
    val vizDataStore: SimulationDataCollector by inject(qualifier = VIZ_DATA_STORE)

    // Статистика обслуживания
    val maintenanceCounter = AtomicInteger(0)
    val reagentMaintenanceCounter = AtomicInteger(0)
    val wasteMaintenanceCounter = AtomicInteger(0)

    override fun process(): Sequence<Component> = sequence {
        while(true) {
            log("MaintenanceTech: passivate, waiting for signals.")
            passivate()
            log("MaintenanceTech: awakened -> reagent=${needsReagentMaintenance.value}, waste=${needsWasteMaintenance.value}")

            if(!needsReagentMaintenance.value && !needsWasteMaintenance.value) {
                log("No tasks => continue")
                continue
            }

            // Увеличиваем счетчик обслуживаний
            maintenanceCounter.incrementAndGet()

            request(analyzer withPriority Priority.CRITICAL)
            if(failed) {
                log("Could not acquire analyzer => skip maintenance.")
                continue
            }

            val doReagent = needsReagentMaintenance.value
            val doWaste = needsWasteMaintenance.value

            val newState = when {
                doReagent && doWaste -> AnalyzerState.MAINTENANCE
                doReagent -> AnalyzerState.MAINTENANCE_REAGENT
                doWaste -> AnalyzerState.MAINTENANCE_WASTE
                else -> AnalyzerState.IDLE
            }
            analyzerState.value = newState

            // Записываем время начала обслуживания
            val maintenanceStartTime = env.now.toTickTime().value

            hold(AnalyzerConfig.TECHNICIAN_PREP_TIME, "Technician prep")

            if(doReagent) {
                reagentMaintenanceCounter.incrementAndGet()

                val reagents = listOf(reagentDiluent, reagentLyse, reagentSheath, reagentDetergent)
                var refillTime = Duration.ZERO
                for(r in reagents) {
                    val needed = (r.capacity - r.level)
                    if(needed > 0.01) {
                        put(r withQuantity needed, capacityLimitMode = CapacityLimitMode.SCHEDULE)
                        if(!failed) {
                            refillTime += AnalyzerConfig.REAGENT_REPLACE_TIME
                            log("Refilled ${r.name}: +$needed ml")

                            // Обновляем уровень реагента для визуализации
                            vizDataStore.addReagentLevel(env.now.toTickTime().value, r.name, r.level)
                        } else {
                            log("ERROR: can't refill ${r.name}")
                        }
                    }
                }
                if(refillTime > Duration.ZERO) {
                    hold(refillTime, "Reagent replacement time")
                }
                needsReagentMaintenance.value = false
            }

            if(doWaste) {
                wasteMaintenanceCounter.incrementAndGet()

                val disposeVolume = wasteContainer.level
                if(disposeVolume > 0.01) {
                    take(wasteContainer, disposeVolume)
                    if(!failed) {
                        hold(AnalyzerConfig.WASTE_DISPOSAL_TIME, "Disposing waste")
                        log("Disposed waste: $disposeVolume ml")

                        // Обновляем уровень отходов для визуализации
                        vizDataStore.addWasteLevel(env.now.toTickTime().value, wasteContainer.level)
                    }
                }
                needsWasteMaintenance.value = false
            }

            hold(AnalyzerConfig.MAINTENANCE_FINISH_TIME, "Finishing maintenance")

            // Записываем время завершения обслуживания
            val maintenanceEndTime = env.now.toTickTime().value
            val maintenanceDuration = maintenanceEndTime - maintenanceStartTime
            log("Maintenance completed in $maintenanceDuration seconds")

            // Записываем статистику времени обслуживания
            get<NumericStatisticMonitor>(qualifier = MAINTENANCE_TIME_MONITOR).addValue(maintenanceDuration)

            // Добавляем событие обслуживания для визуализации
            val maintenanceType = when {
                doReagent && doWaste -> "Both"
                doReagent -> "Reagent"
                doWaste -> "Waste"
                else -> "Unknown"
            }
            vizDataStore.addMaintenanceEvent(maintenanceStartTime, maintenanceEndTime, maintenanceType)

            analyzerState.value = AnalyzerState.IDLE

            get<AnalyzerController>().activate()
            release(analyzer)
            log("Maintenance done.")
        }
    }
}

// Квалификаторы для зависимостей
val ANALYZER_RESOURCE: StringQualifier = named("analyzer_resource")
val ANALYSIS_QUEUE: StringQualifier = named("analysis_queue")
val ANALYZER_STATE: StringQualifier = named("analyzer_state")
val ANALYZER_STATE_TIMELINE: StringQualifier = named("analyzer_state_timeline")
val REAGENT_DILUENT: StringQualifier = named("reagent_diluent")
val REAGENT_LYSE: StringQualifier = named("reagent_lyse")
val REAGENT_SHEATH: StringQualifier = named("reagent_sheath")
val REAGENT_DETERGENT: StringQualifier = named("reagent_detergent")
val WASTE_CONTAINER: StringQualifier = named("waste_container")
val NEEDS_MAINTENANCE_REAGENT: StringQualifier = named("needs_maintenance_reagent")
val NEEDS_MAINTENANCE_WASTE: StringQualifier = named("needs_maintenance_waste")
val TIME_TRACKER: StringQualifier = named("time_tracker")
val VIZ_DATA_STORE: StringQualifier = named("viz_data_store")

// Мониторы для статистики
val TURNAROUND_TIME_MONITOR: StringQualifier = named("turnaround_time")
val TURNAROUND_TIME_MONITOR_INTERRUPTED: StringQualifier = named("turnaround_time_interrupted")
val TURNAROUND_TIME_MONITOR_FAILED: StringQualifier = named("turnaround_time_failed")
val WAIT_TIME_MONITOR: StringQualifier = named("wait_time")
val PROCESS_TIME_MONITOR: StringQualifier = named("process_time")
val MAINTENANCE_TIME_MONITOR: StringQualifier = named("maintenance_time")
val QUEUE_LENGTH_MONITOR: StringQualifier = named("queue_length")

class Urit5380Simulation(enableTracing: Boolean = true)
    : Environment(enableComponentLogger = enableTracing, tickDurationUnit = DurationUnit.SECONDS) {

    // Ресурсы и очереди
    val analyzerResource = dependency(qualifier = ANALYZER_RESOURCE) {
        Resource("URIT-5380", capacity = 1)
    }
    val analysisQueue = dependency(qualifier = ANALYSIS_QUEUE) {
        ComponentQueue(
            "AutoloaderQueue",
            capacity = AnalyzerConfig.AUTOLOADER_CAPACITY,
            q = PriorityQueue(compareBy<CQElement<BloodSample>> { it.priority }.thenBy { it.enterTime })
        )
    }

    val analyzerState = dependency(qualifier = ANALYZER_STATE) {
        State(AnalyzerState.IDLE, "Analyzer Status")
    }
    val analyzerStateTimeline = dependency(qualifier = ANALYZER_STATE_TIMELINE) {
        CategoryTimeline(AnalyzerState.IDLE, "Analyzer State Timeline")
    }

    // Хранилище данных для визуализации
    val vizDataStore: SimulationDataCollector = dependency(qualifier = VIZ_DATA_STORE) {
        VisualizationDataStore() as SimulationDataCollector
    }

    // Трекер времени
    val timeTracker = dependency(qualifier = TIME_TRACKER) {
        TimeTracker()
    }

    // Отслеживание изменений состояния
    init {
        analyzerState.onChange { record ->
            analyzerStateTimeline.addValue(record.value)
            println("Analyzer state changed -> ${record.value} at ${now.toTickTime().value}")

            // Добавляем изменение состояния для визуализации
            vizDataStore.addStateChange(now.toTickTime().value, record.value)
        }
        analyzerStateTimeline.addValue(analyzerState.value)
    }

    // Ресурсы реагентов
    val reagentDiluent = dependency(qualifier = REAGENT_DILUENT) {
        DepletableResource("Diluent", AnalyzerConfig.DILUENT_CAPACITY, AnalyzerConfig.DILUENT_CAPACITY)
    }
    val reagentLyse = dependency(qualifier = REAGENT_LYSE) {
        DepletableResource("Lyse", AnalyzerConfig.LYSE_CAPACITY, AnalyzerConfig.LYSE_CAPACITY)
    }
    val reagentSheath = dependency(qualifier = REAGENT_SHEATH) {
        DepletableResource("Sheath Fluid", AnalyzerConfig.SHEATH_CAPACITY, AnalyzerConfig.SHEATH_CAPACITY)
    }
    val reagentDetergent = dependency(qualifier = REAGENT_DETERGENT) {
        DepletableResource("Detergent", AnalyzerConfig.DETERGENT_CAPACITY, AnalyzerConfig.DETERGENT_CAPACITY)
    }
    val wasteContainer = dependency(qualifier = WASTE_CONTAINER) {
        DepletableResource("Waste Container", AnalyzerConfig.WASTE_CAPACITY, 0.0)
    }

    // Состояния обслуживания
    val needsReagentMaintenance = dependency(qualifier = NEEDS_MAINTENANCE_REAGENT) {
        State(false, "Needs Reagent Maintenance")
    }
    val needsWasteMaintenance = dependency(qualifier = NEEDS_MAINTENANCE_WASTE) {
        State(false, "Needs Waste Maintenance")
    }

    // Мониторы статистики времени
    val turnaroundTimeMonitor = dependency(qualifier = TURNAROUND_TIME_MONITOR) {
        NumericStatisticMonitor("Sample TAT (sec)")
    }
    val interruptedTatMonitor = dependency(qualifier = TURNAROUND_TIME_MONITOR_INTERRUPTED) {
        NumericStatisticMonitor("Interrupted Sample TAT (sec)")
    }
    val failedTatMonitor = dependency(qualifier = TURNAROUND_TIME_MONITOR_FAILED) {
        NumericStatisticMonitor("Failed Sample TAT (sec)")
    }
    val waitTimeMonitor = dependency(qualifier = WAIT_TIME_MONITOR) {
        NumericStatisticMonitor("Sample Wait Time (sec)")
    }
    val processTimeMonitor = dependency(qualifier = PROCESS_TIME_MONITOR) {
        NumericStatisticMonitor("Sample Process Time (sec)")
    }
    val maintenanceTimeMonitor = dependency(qualifier = MAINTENANCE_TIME_MONITOR) {
        NumericStatisticMonitor("Maintenance Time (sec)")
    }

    // Монитор для длины очереди
    val queueLengthMonitor = dependency(qualifier = QUEUE_LENGTH_MONITOR) {
        NumericStatisticMonitor("Queue Length")
    }

    // Компоненты
    val analyzerController = dependency { AnalyzerController() }
    val maintenanceTechnician = dependency { MaintenanceTechnician() }

    // Инициализация
    init {
        // Генератор образцов
        val modeDist = enumerated(AnalyzerConfig.MODE_PROBABILITIES)

        ComponentGenerator(iat = normal(AnalyzerConfig.SAMPLE_ARRIVAL_MEAN, AnalyzerConfig.SAMPLE_ARRIVAL_SD)) { idx ->
            val sampleId = "Sample_${now.toTickTime().value.toInt()}_$idx"
            val mode = modeDist()
            val newSample = BloodSample(sampleId, mode, false)

            println("Generated new sample $sampleId at time ${now.toTickTime().value}, mode=$mode")

            // Проверка на переполнение очереди
            if(analysisQueue.size < analysisQueue.capacity) {
                // Создаем образец, но не добавляем его в очередь
                // Добавление в очередь происходит в методе process() образца

                // Активируем контроллер для обработки образца
                newSample.activate()
                analyzerController.activate()

                // Регистрируем текущую длину очереди
                queueLengthMonitor.addValue(analysisQueue.size.toDouble())
            } else {
                println("Queue full, sample $sampleId discarded")
                newSample.cancel()
            }
        }

        // Периодический контроллер состояния
        object : Component("StatusChecker") {
            override fun process(): Sequence<Component> = sequence {
                while(true) {
                    hold(10.minutes, "Periodic check")
                    log("StatusChecker: checking reagents & waste...")

                    // Регистрируем текущие уровни реагентов
                    log("Current reagent levels: " +
                            "Diluent=${reagentDiluent.level}/${reagentDiluent.capacity}, " +
                            "Lyse=${reagentLyse.level}/${reagentLyse.capacity}, " +
                            "Sheath=${reagentSheath.level}/${reagentSheath.capacity}, " +
                            "Waste=${wasteContainer.level}/${wasteContainer.capacity}")

                    // Обновляем уровни для визуализации
                    vizDataStore.addReagentLevel(now.toTickTime().value, "Diluent", reagentDiluent.level)
                    vizDataStore.addReagentLevel(now.toTickTime().value, "Lyse", reagentLyse.level)
                    vizDataStore.addReagentLevel(now.toTickTime().value, "Sheath", reagentSheath.level)
                    vizDataStore.addReagentLevel(now.toTickTime().value, "Detergent", reagentDetergent.level)
                    vizDataStore.addWasteLevel(now.toTickTime().value, wasteContainer.level)

                    // Проверка уровня реагентов
                    if(!needsReagentMaintenance.value) {
                        val lowReagents = listOf(reagentDiluent, reagentLyse, reagentSheath, reagentDetergent).filter {
                            val pct = it.level / it.capacity * 100.0
                            pct < AnalyzerConfig.REAGENT_LOW_THRESHOLD_PCT
                        }
                        if(lowReagents.isNotEmpty()) {
                            log("Low reagents: ${lowReagents.joinToString { it.name }}")
                            needsReagentMaintenance.value = true
                            maintenanceTechnician.activate()
                        }
                    }

                    // Проверка уровня отходов
                    if(!needsWasteMaintenance.value) {
                        val fillPct = wasteContainer.level / wasteContainer.capacity * 100.0
                        if(fillPct > AnalyzerConfig.WASTE_HIGH_THRESHOLD_PCT) {
                            log("Waste container ~ full ($fillPct%)")
                            needsWasteMaintenance.value = true
                            maintenanceTechnician.activate()
                        }
                    }

                    // Регистрируем текущую длину очереди
                    queueLengthMonitor.addValue(analysisQueue.size.toDouble())
                    vizDataStore.addQueueLength(now.toTickTime().value, analysisQueue.size)
                }
            }
        }
    }

    // Метод для получения хранилища данных визуализации
    fun getVisualizationDataStore(): VisualizationDataStore {
        return vizDataStore as VisualizationDataStore
    }

    // Вывод основной статистики симуляции в консоль
    fun printStatistics() {
        println("\n--- Simulation ended after ${now.epochSeconds.seconds} sec ---")

        // Вывод статистики
        println("\nAnalyzer State Distribution:")
        analyzerStateTimeline.printHistogram()

        println("\nAutoloader Queue Length distribution:")
        analysisQueue.sizeTimeline.printHistogram()
        analysisQueue.printHistogram()

        println("\nQueue Length Statistics:")
        println(queueLengthMonitor.statistics())
        queueLengthMonitor.printHistogram()

        println("\nReagent Levels (final):")
        listOf(
            reagentDiluent,
            reagentLyse,
            reagentSheath,
            reagentDetergent
        ).forEach {
            println("  ${it.name}: level=%.2f / %.2f".format(it.level, it.capacity))
        }

        println("\nWaste Container fill timeline:")
        val wasteTLine = wasteContainer.levelTimeline.asDoubleTimeline() / wasteContainer.capacity * 100.0
        wasteTLine.printHistogram()

        println("\nSample Statistics:")
        println("Completed: ${BloodSample.completedSamples.get()}")
        println("Interrupted (RRBC): ${BloodSample.interruptedSamples.get()}")
        println("Failed (errors): ${BloodSample.errorSamples.get()}")
        println("Total: ${BloodSample.completedSamples.get() + BloodSample.interruptedSamples.get() + BloodSample.errorSamples.get()}")

        println("\nSample Turnaround Time (sec):")
        if(turnaroundTimeMonitor.values.isEmpty()) {
            println("No completed samples => no TAT data.")
        } else {
            turnaroundTimeMonitor.printHistogram()
            println(turnaroundTimeMonitor.statistics())
        }

        println("\nSample Wait Time (sec):")
        if(waitTimeMonitor.values.isEmpty()) {
            println("No wait time data.")
        } else {
            waitTimeMonitor.printHistogram()
            println(waitTimeMonitor.statistics())
        }

        println("\nSample Process Time (sec):")
        if(processTimeMonitor.values.isEmpty()) {
            println("No process time data.")
        } else {
            processTimeMonitor.printHistogram()
            println(processTimeMonitor.statistics())
        }

        println("\nInterrupted Sample Turnaround Time (sec):")
        if(interruptedTatMonitor.values.isEmpty()) {
            println("No interrupted samples => no data.")
        } else {
            interruptedTatMonitor.printHistogram()
            println(interruptedTatMonitor.statistics())
        }

        println("\nFailed Sample Turnaround Time (sec):")
        if(failedTatMonitor.values.isEmpty()) {
            println("No failed samples => no data.")
        } else {
            failedTatMonitor.printHistogram()
            println(failedTatMonitor.statistics())
        }

        println("\nMaintenance Statistics:")
        println("Total maintenance sessions: ${get<MaintenanceTechnician>().maintenanceCounter.get()}")
        println("Reagent maintenance: ${get<MaintenanceTechnician>().reagentMaintenanceCounter.get()}")
        println("Waste maintenance: ${get<MaintenanceTechnician>().wasteMaintenanceCounter.get()}")

        println("\nMaintenance Time (sec):")
        if(maintenanceTimeMonitor.values.isEmpty()) {
            println("No maintenance => no data.")
        } else {
            maintenanceTimeMonitor.printHistogram()
            println(maintenanceTimeMonitor.statistics())
        }

        println("\nAnalyzer usage stats:")
        analyzerResource.printStatistics()
    }
}

fun main() {
    println("Starting Urit5380 Simulation for 4h...")
    val sim = Urit5380Simulation(enableTracing = true)
    sim.run(4.hours)
    sim.printStatistics()
    println("\nSimulation complete.")
}