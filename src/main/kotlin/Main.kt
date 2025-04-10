package space.kscience

import kotlin.time.Duration.Companion.hours

fun runSimulationWithVisualization() {
    println("Starting Urit5380 Simulation for 4h...")
    val sim = Urit5380Simulation(enableTracing = true)
    sim.run(4.hours)

    sim.printStatistics()

    val visualizer = Urit5380Visualizer(sim.getVisualizationDataStore())
    visualizer.saveAllPlotsToHtml("urit5380_simulation_results.html", "4h")

    println("\nVisualization complete. Check HTML output file.")
}

fun main() {
    runSimulationWithVisualization()
}