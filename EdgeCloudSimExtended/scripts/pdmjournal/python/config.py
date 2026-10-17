from pathlib import Path

def get_configuration():
    script_dir = Path(__file__).resolve().parent

    results_dir = (
        script_dir / "../../../sim_results/pdmjournal"
    ).resolve()

    return {
        "folder_path": str(results_dir),
        "num_iterations": 6,

        "scenario_types": [
            "CLOUD_FIRST",
            "OFFLINE_DIRECT",
            "EDGE_ONLY",
            "HES",
        ],
        "legends": [
            "Cloud First",
            "Offline Direct",
            "Edge Only",
            "HES",
        ],

        # [unused left, unused bottom, width in cm, height in cm]
        "figure_position": [6, 3, 15, 15],
        # [axis labels, legend, tick labels]
        "font_sizes": [13, 12, 12],
        "x_axis_label": "Number of mobile devices",

        "min_devices": 5000,
        "step_devices": 5000,
        "max_devices": 50000,

        "save_figure_as_pdf": True,
        "plot_confidence_interval": True,
        "use_color": True,

        "colors": [
            [0.55, 0.00, 0.00],
            [0.00, 0.15, 0.60],
            [0.00, 0.23, 0.00],
            [0.60, 0.00, 0.60],
        ],
        "line_styles": ["-", "--", "-.", ":"],
        "markers": ["*", "o", "s", "v"],

        # Used by the HESLogger CSV reader. The reader also supports the
        # legacy filename alternatives implemented in plotHESMetrics.py.
        "hes_file_pattern": "HES_%s_%d.csv",
    }
