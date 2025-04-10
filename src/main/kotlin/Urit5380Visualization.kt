package space.kscience

import kotlinx.html.*
import space.kscience.dataforge.values.asValue
import space.kscience.plotly.*
import space.kscience.plotly.models.*
import space.kscience.plotly.palettes.T10
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.roundToInt

/**
 * Класс визуализации для результатов симуляции URIT-5380.
 * Получает данные через [VisualizationDataStore] и создает графики Plotly.
 *
 * @property dataStore Хранилище данных симуляции.
 */
class Urit5380Visualizer(private val dataStore: VisualizationDataStore) {

    private val timeFactor = 1000.0

    /**
     * Создает график временной шкалы состояний анализатора.
     * Отображает переключение состояний во времени.
     * @return Объект [Plot].
     */
    fun createAnalyzerStateTimeline(): Plot {
        val data = dataStore.stateChanges
        if (data.isEmpty()) return Plotly.plot { layout { title = "Analyzer State Timeline (No Data)" } }

        // Преобразуем время в секунды
        val timePoints = data.map { it.first / timeFactor }.toDoubleArray()
        val states = data.map { it.second.toString() }.toTypedArray()

        return Plotly.plot {
            scatter {
                x.set(timePoints)
                y.set(states)
                mode = ScatterMode.lines
                line {
                    shape = LineShape.hv
                    width = 2.0
                    color(T10.BLUE)
                }
                name = "Analyzer State"
            }
            layout {
                title = "Analyzer State Timeline"
                xaxis {
                    title = "Time (seconds)"
                }
                yaxis {
                    title = "State"
                    type = AxisType.category
                    tickfont { size = 10 }
                }
                height = 450
                margin { l = 100; r = 30; b = 50; t = 50 }
                showlegend = false
            }
        }
    }

    /**
     * Создает Box Plot для времени ожидания, обработки и общего TAT образцов.
     * @return Объект [Plot].
     */
    fun createSampleTurnaroundTimeBoxPlot(): Plot {
        val completedSamples = dataStore.samples.filter { it.status == SampleStatus.COMPLETED }
        if (completedSamples.isEmpty()) return Plotly.plot { layout { title = "Sample Processing Time Breakdown (No Data)" } }

        val waitTimes = completedSamples.map {
            it.analysisStartTime.let { start -> (start - it.queueTime) / timeFactor }
        }.ifEmpty { null }?.toDoubleArray()

        val processTimes = completedSamples.map {
            (it.analysisEndTime - it.analysisStartTime) / timeFactor
        }.ifEmpty { null }?.toDoubleArray()

        val totalTimes = completedSamples.map {
            it.analysisEndTime.let { end -> (end - it.queueTime) / timeFactor }
        }.ifEmpty { null }?.toDoubleArray()


        return Plotly.plot {
            if (waitTimes != null) {
                box {
                    y.set(waitTimes)
                    name = "Wait Time"
                    boxmean = BoxMean.`true`
                    marker { color(T10.BLUE) }
                }
            }
            if (processTimes != null) {
                box {
                    y.set(processTimes)
                    name = "Process Time"
                    boxmean = BoxMean.`true`
                    marker { color(T10.ORANGE) }
                }
            }
            if (totalTimes != null) {
                box {
                    y.set(totalTimes)
                    name = "Total TAT"
                    boxmean = BoxMean.`true`
                    marker { color(T10.GREEN) }
                }
            }
            layout {
                title = "Sample Processing Time Breakdown"
                yaxis {
                    title = "Time (seconds)"
                    zeroline = false
                }
                height = 500
                margin { l = 50; r = 30; b = 50; t = 50 }
                showlegend = true
            }
        }
    }

    /**
     * Создает группированный Box Plot для общего TAT образцов по режимам анализа.
     * @return Объект [Plot].
     */
    fun createTurnaroundTimeByModeBoxPlot(): Plot {
        val completedSamples = dataStore.samples.filter { it.status == SampleStatus.COMPLETED }
        if (completedSamples.isEmpty()) return Plotly.plot { layout { title = "Sample Turnaround Time by Mode (No Data)" } }

        val byMode = completedSamples.groupBy { it.mode }

        return Plotly.plot {
            layout {
                title = "Sample Turnaround Time by Mode"
                yaxis {
                    title = "Time (seconds)"
                    zeroline = false
                }
                boxmode = BoxMode.group
                height = 500
                margin { l = 50; r = 30; b = 50; t = 50 }
                showlegend = true
            }

            byMode.entries.sortedBy { it.key.toString() }.forEach { (mode, samples) ->
                val tatTimes = samples.map {
                    it.analysisEndTime.let { end -> (end - it.queueTime) / timeFactor }
                }.ifEmpty { null }?.toDoubleArray()

                if (tatTimes != null) {
                    box {
                        y.set(tatTimes)
                        name = mode.toString()
                        boxmean = BoxMean.`true`
                    }
                }
            }
        }
    }

    /**
     * Создает круговую диаграмму (Donut Chart) для распределения образцов по статусам.
     * @return Объект [Plot].
     */
    fun createSampleStatusPie(): Plot {
        if (dataStore.samples.isEmpty()) return Plotly.plot { layout { title = "Sample Status Distribution (No Data)" } }

        val statusCounts = dataStore.samples
            .groupingBy { it.status }
            .eachCount()
            .entries.sortedBy { it.key.toString() }

        if (statusCounts.isEmpty()) return Plotly.plot { layout { title = "Sample Status Distribution (No Data)" } }

        val labelsList = statusCounts.map { it.key.toString() }
        val valuesList = statusCounts.map { it.value }

        return Plotly.plot {
            pie {
                labels(labelsList)
                values(valuesList)
                hole = 0.4
                textinfo = TextInfo.`label+percent`
                pull = 0.02
                marker {
                    line {
                        color("white")
                        width = 1.0
                    }
                }
            }
            layout {
                title = "Sample Status Distribution"
                height = 500
                margin { l = 30; r = 30; b = 50; t = 50 }
                showlegend = true
            }
        }
    }

    /**
     * Создает линейный график для уровней реагентов и отходов во времени.
     * @return Объект [Plot].
     */
    fun createReagentLevelsChart(): Plot {
        val hasReagentData = dataStore.reagentLevels.any { it.value.isNotEmpty() }
        val hasWasteData = dataStore.wasteLevels.isNotEmpty()
        if (!hasReagentData && !hasWasteData) return Plotly.plot { layout { title = "Reagent Levels Over Time (No Data)" } }

        val plot = Plotly.plot {
            layout {
                title = "Reagent & Waste Levels Over Time"
                xaxis {
                    title = "Time (seconds)"
                }
                yaxis {
                    title = "Level (units or ml)"
                    autorange = true
                }
                height = 500
                margin { l = 50; r = 120; b = 50; t = 50 }
                showlegend = true
                legend {
                    x = 1.02
                    y = 1.0
                    xanchor = XAnchor.left
                }
            }
        }

        dataStore.reagentLevels.entries.sortedBy { it.key }.forEach { (reagentName, levels) ->
            if (levels.isNotEmpty()) {
                val timePoints = levels.map { it.first / timeFactor }.toDoubleArray()
                val levelValues = levels.map { it.second }.toDoubleArray()

                plot.scatter {
                    x.set(timePoints)
                    y.set(levelValues)
                    mode = ScatterMode.lines
                    name = reagentName
                }
            }
        }

        if (hasWasteData) {
            val wasteTimePoints = dataStore.wasteLevels.map { it.first / timeFactor }.toDoubleArray()
            val wasteLevels = dataStore.wasteLevels.map { it.second }.toDoubleArray()

            plot.scatter {
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

        return plot
    }

    /**
     * Создает ступенчатый линейный график длины очереди ожидания образцов.
     * @return Объект [Plot].
     */
    fun createQueueLengthChart(): Plot {
        if (dataStore.queueLengths.isEmpty()) return Plotly.plot { layout { title = "Queue Length Over Time (No Data)" } }

        val timePoints = dataStore.queueLengths.map { it.first / timeFactor }.toDoubleArray()

        val queueLengths = dataStore.queueLengths.map { it.second }

        return Plotly.plot {
            scatter {
                x.set(timePoints)
                y.set(queueLengths)
                mode = ScatterMode.lines
                name = "Queue Length"
                line {
                    shape = LineShape.hv
                    color(T10.GREEN)
                    width = 2.0
                }
                fill = FillType.tozeroy
                fillcolor("rgba(44, 160, 44, 0.2)")
            }
            layout {
                title = "Queue Length Over Time"
                xaxis {
                    title = "Time (seconds)"
                }
                yaxis {
                    title = "Number of Samples"
                    dtick = 1.0.asValue()
                }
                height = 500
                margin { l = 50; r = 30; b = 50; t = 50 }
                showlegend = false
            }
        }
    }

    /**
     * Создает диаграмму Ганта (используя Bar chart) для отображения событий и длительности обслуживания.
     * @return Объект [Plot].
     */
    fun createMaintenanceEventsChart(): Plot {
        if (dataStore.maintenanceEvents.isEmpty()) return Plotly.plot { layout { title = "Maintenance Events (No Data)" } }

        return Plotly.plot {
            layout {
                title = "Maintenance Events Duration"
                xaxis {
                    title = "Time (seconds)"
                }
                yaxis {
                    title = "Maintenance Type"
                    type = AxisType.category // Типы обслуживания как категории
                }
                barmode = BarMode.overlay
                height = 400
                margin { l = 100; r = 30; b = 50; t = 50 }
                showlegend = true
                legend {}
            }

            val byType = dataStore.maintenanceEvents.groupBy { it.third }.entries.sortedBy { it.key }

            byType.forEach { (type, events) ->
                val startTimes = events.map { it.first / timeFactor }
                val durations = events.map { (it.second - it.first) / timeFactor }
                val yValues = Array(events.size) { type }

                bar {
                    base = startTimes
                    x.set(durations)
                    y.set(yValues)
                    orientation = Orientation.h
                    name = type
                    marker {
                        color(
                            when (type) {
                                "Reagent" -> T10.BLUE
                                "Waste" -> T10.RED
                                "Both" -> T10.PURPLE
                                else -> T10.GRAY
                            }
                        )
                        opacity = 0.7
                    }
                    textposition = TextPosition.auto
                    showlegend = true
                }
            }
        }
    }

    /**
     * Создает гистограмму распределения общего времени обработки (TAT) завершенных образцов.
     * @return Объект [Plot].
     */
    fun createTatHistogram(): Plot {
        val completedSamples = dataStore.samples.filter { it.status == SampleStatus.COMPLETED }
        if (completedSamples.isEmpty()) return Plotly.plot { layout { title = "Turnaround Time Distribution (No Data)" } }

        val tatTimes = completedSamples.map {
            it.analysisEndTime.let { end -> (end - it.queueTime) / timeFactor }
        }.ifEmpty { null }?.toDoubleArray()

        if (tatTimes == null) return Plotly.plot { layout { title = "Turnaround Time Distribution (No Data)" } }

        return Plotly.plot {
            histogram {
                x.set(tatTimes)
                name = "TAT Distribution"
                marker {
                    color(T10.CYAN)
                    line {
                        color("white")
                        width = 0.5
                    }
                }
                opacity = 0.75
            }
            layout {
                title = "Turnaround Time (TAT) Distribution"
                xaxis {
                    title = "Time (seconds)"
                }
                yaxis {
                    title = "Frequency (Number of Samples)"
                }
                bargap = 0.05
                height = 500
                margin { l = 50; r = 30; b = 50; t = 50 }
                showlegend = false
            }
        }
    }

    /**
     * Собирает все графики в единый HTML-отчет и сохраняет его в файл.
     *
     * @param filepath Путь к файлу для сохранения HTML-отчета.
     * @param simulationDuration Строка, описывающая длительность симуляции для заголовка.
     */
    fun saveAllPlotsToHtml(filepath: String, simulationDuration: String = "N/A") {
        // Генерация всех графиков
        val statePlot = createAnalyzerStateTimeline()
        val tatBoxPlot = createSampleTurnaroundTimeBoxPlot()
        val tatModeBoxPlot = createTurnaroundTimeByModeBoxPlot()
        val statusPie = createSampleStatusPie()
        val reagentPlot = createReagentLevelsChart()
        val queuePlot = createQueueLengthChart()
        val maintenancePlot = createMaintenanceEventsChart()
        val tatHistogram = createTatHistogram()

        // Создание HTML страницы с использованием Bootstrap для разметки
        val page = Plotly.page(
            cdnPlotlyHeader,
            cdnBootstrap,
            title = "URIT-5380 Simulation Results"
        ) { renderer ->
            div("container-fluid mt-3") {
                h1("mb-3") { +"URIT-5380 Simulation Visualization Report" }
                h2("mb-4 text-muted") { +"Simulation Duration: $simulationDuration" }
                hr()

                // --- Ряд 1: Состояние и общая статистика времени ---
                div("row align-items-stretch mb-4") {
                    div("col-lg-7 mb-3 mb-lg-0") {
                        div("card h-100") {
                            div("card-header") { h4("card-title mb-0") { +"Analyzer State Timeline" } }
                            div("card-body") {
                                plot(statePlot, renderer = renderer)
                            }
                        }
                    }
                    div("col-lg-5") {
                        div("card h-100") {
                            div("card-header") { h4("card-title mb-0") { +"Sample Processing Time Breakdown" } }
                            div("card-body") {
                                plot(tatBoxPlot, renderer = renderer)
                            }
                        }
                    }
                }
                hr()

                // --- Ряд 2: TAT по режимам и статусы образцов ---
                div("row align-items-stretch mb-4") {
                    div("col-md-7 mb-3 mb-md-0") {
                        div("card h-100") {
                            div("card-header") { h4("card-title mb-0") { +"Turnaround Time by Mode" } }
                            div("card-body") {
                                plot(tatModeBoxPlot, renderer = renderer)
                            }
                        }
                    }
                    div("col-md-5") {
                        div("card h-100") {
                            div("card-header") { h4("card-title mb-0") { +"Sample Status Distribution" } }
                            div("card-body") {
                                plot(statusPie, renderer = renderer)
                            }
                        }
                    }
                }
                hr()

                // --- Ряд 3: Реагенты и длина очереди ---
                div("row align-items-stretch mb-4") {
                    div("col-md-6 mb-3 mb-md-0") {
                        div("card h-100") {
                            div("card-header") { h4("card-title mb-0") { +"Reagent & Waste Levels" } }
                            div("card-body") {
                                plot(reagentPlot, renderer = renderer)
                            }
                        }
                    }
                    div("col-md-6") {
                        div("card h-100") {
                            div("card-header") { h4("card-title mb-0") { +"Queue Length" } }
                            div("card-body") {
                                plot(queuePlot, renderer = renderer)
                            }
                        }
                    }
                }
                hr()

                // --- Ряд 4: Обслуживание и гистограмма TAT ---
                div("row align-items-stretch mb-4") {
                    div("col-lg-7 mb-3 mb-lg-0") {
                        div("card h-100") {
                            div("card-header") { h4("card-title mb-0") { +"Maintenance Events Duration" } }
                            div("card-body") {
                                plot(maintenancePlot, renderer = renderer)
                            }
                        }
                    }
                    div("col-lg-5") {
                        div("card h-100") {
                            div("card-header") { h4("card-title mb-0") { +"Turnaround Time Histogram" } }
                            div("card-body") {
                                plot(tatHistogram, renderer = renderer)
                            }
                        }
                    }
                }
                hr()
            }
        }

        val absolutePath: Path = Paths.get(filepath).toAbsolutePath()

        try {
            page.makeFile(absolutePath, show = false)
            println("Plots successfully saved to $absolutePath")
        } catch (e: Exception) {
            System.err.println("Error saving plots to $absolutePath: ${e.message}")
            e.printStackTrace()
        }

        println("\nVisualization generation complete.")
    }
}
