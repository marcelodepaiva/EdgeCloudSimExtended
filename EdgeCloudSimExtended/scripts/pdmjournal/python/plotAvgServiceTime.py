from plotHESMetrics import plot_hes_metric


if __name__ == '__main__':
    print('--- Generating: HES Service and Delay Metrics ---')

    # All four strategies are ALWAYS shown in the legend.
    # Non-applicable strategies have no plotted curve/value.

    plot_hes_metric(
        'end_to_end_latency',
        'Average End-to-End Latency (s)',
        'HES_EndToEndLatency.pdf'
    )

    plot_hes_metric(
        'sync_delay',
        'Average Synchronization Delay (s)',
        'HES_SynchronizationDelay.pdf'
    )

    plot_hes_metric(
        'edge_to_cloud_delay',
        'Average Edge-to-Cloud Delay (s)',
        'HES_EdgeToCloudDelay.pdf'
    )

