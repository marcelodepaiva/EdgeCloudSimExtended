from plotHESMetrics import plot_hes_metric


if __name__ == '__main__':
    print('--- Generating: HES Processing Time Plots ---')

    # Standardized legend: always all four strategies.
    plot_hes_metric(
        'processing_time',
        'Average Processing Time (s)',
        'HES_AverageProcessingTime.pdf'
    )

    plot_hes_metric(
        'edge_processing_time',
        'Average Edge Processing Time (s)',
        'HES_EdgeProcessingTime.pdf'
    )

    plot_hes_metric(
        'cloud_processing_time',
        'Average Cloud Processing Time (s)',
        'HES_CloudProcessingTime.pdf'
    )
