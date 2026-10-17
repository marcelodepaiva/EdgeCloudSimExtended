from plotGenericLine import plot_generic_line


if __name__ == "__main__":
    print("--- Generating: Average VM CPU Utilization ---")

    plot_generic_line(
        2, 8,
        "Average Edge VM CPU Utilization (fraction)",
        "ALL_APPS",
        calculate_percentage=None,
        legend_pos="upper left"
    )

    plot_generic_line(
        3, 8,
        "Average Cloud VM CPU Utilization (fraction)",
        "ALL_APPS",
        calculate_percentage=None,
        legend_pos="upper left"
    )