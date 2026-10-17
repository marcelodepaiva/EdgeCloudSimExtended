"""
Plot HESLogger metrics from per-iteration CSV files.

The script:
- computes metrics from event timestamps;
- reports missing files and invalid observations;
- computes a mean per simulation, then a mean and t-based 95% CI across
  simulation repetitions (not across individual tasks);
- avoids treating missing repetitions as zero;
- saves PDFs in the configured results directory.

Metric definitions must match the event semantics implemented by HESLogger.
"""
from pathlib import Path
import re

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy.stats import t

from config import get_configuration


TIMESTAMP_COLUMNS = [
    "creation_time",
    "edge_start_time",
    "edge_completion_time",
    "cloud_start_time",
    "cloud_completion_time",
    "offline_start_time",
    "sync_start_time",
    "cloud_arrival_time",
]


def _find_hes_csv(folder_path, iteration, scenario, devices):
    """Find one exact CSV match; avoid partial matches such as 5000 vs 50000."""
    config = get_configuration()
    ite_dir = Path(folder_path) / f"ite{iteration}"
        # Busca exata para o padrão de nomes utilizado nas simulações
    expected = (
        ite_dir
        / f"SIMRESULT_SCHOOL_NETWORK_{scenario}_{int(devices)}DEVICES_HES.csv"
    )

    if expected.is_file():
        return str(expected)
    if not ite_dir.is_dir():
        return None

    pattern = config.get("hes_file_pattern", "HES_%s_%d.csv")
    expected = ite_dir / (pattern % (scenario, int(devices)))
    candidates = [
        expected,
        ite_dir / f"HES_{scenario}_{devices}DEVICES.csv",
        ite_dir / f"{scenario}_{devices}.csv",
        ite_dir / f"{scenario}_{devices}DEVICES.csv",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)

    # Flexible fallback with numeric boundaries around the device count.
    scenario_lower = scenario.lower()
    device_pattern = re.compile(rf"(?<!\d){int(devices)}(?!\d)")
    matches = []
    for candidate in ite_dir.glob("*.csv"):
        base = candidate.name.lower()
        if scenario_lower in base and device_pattern.search(candidate.name):
            matches.append(candidate)

    if len(matches) == 1:
        return str(matches[0])
    if len(matches) > 1:
        print(
            f"[WARNING] Ambiguous HES CSV match for iteration={iteration}, "
            f"scenario={scenario}, devices={devices}: "
            + ", ".join(str(p) for p in matches)
        )
    return None


def _read_hes_csv(path):
    """Read a HESLogger CSV and coerce timestamp fields to numeric."""
    df = pd.read_csv(path)
    required = {"event", "task_id"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"{path}: missing required columns {sorted(missing)}")

    # A metric-specific check is performed during extraction. Missing timestamp
    # columns are added as NaN so an unrelated column does not block every plot.
    for column in TIMESTAMP_COLUMNS:
        if column not in df.columns:
            df[column] = np.nan
        df[column] = pd.to_numeric(df[column], errors="coerce")

    df["event"] = df["event"].astype(str).str.strip().str.upper()
    return df


def _calculate_basic_metrics(df):
    """Calculate timestamp-derived metrics only from valid timestamp pairs."""
    df = df.copy()

    def safe_difference(end_column, start_column):
        start = pd.to_numeric(df[start_column], errors="coerce")
        end = pd.to_numeric(df[end_column], errors="coerce")

        valid = (
            start.notna()
            & end.notna()
            & np.isfinite(start)
            & np.isfinite(end)
            & (start >= 0)
            & (end >= 0)
            & (end >= start)
        )

        result = pd.Series(np.nan, index=df.index, dtype=float)
        result.loc[valid] = end.loc[valid] - start.loc[valid]
        return result

    df["end_to_end_latency"] = safe_difference(
        "cloud_completion_time", "creation_time"
    )
    df["edge_processing_time"] = safe_difference(
        "edge_completion_time", "edge_start_time"
    )
    df["cloud_processing_time"] = safe_difference(
        "cloud_completion_time", "cloud_start_time"
    )
    df["sync_delay"] = safe_difference(
        "sync_start_time", "offline_start_time"
    )
    df["edge_to_cloud_delay"] = safe_difference(
        "cloud_arrival_time", "edge_completion_time"
    )

    return df


def _completed_task_sync_delay(df, scenario):
    """Return one synchronization delay per task that also has terminal completion."""
    completion_event = {
        "CLOUD_FIRST": "CLOUD_COMPLETE",
        "OFFLINE_DIRECT": "CLOUD_COMPLETE",
        "HES": "CLOUD_COMPLETE",
        "EDGE_ONLY": "EDGE_COMPLETE",
    }
    if scenario not in completion_event:
        raise ValueError(f"Unknown scenario for completed-task sync delay: {scenario}")

    sync_df = df.loc[
        df["event"].eq("SYNC"),
        ["task_id", "offline_start_time", "sync_start_time"],
    ].copy()
    sync_df = sync_df.dropna(
        subset=["task_id", "offline_start_time", "sync_start_time"]
    )

    valid_timestamps = (
        np.isfinite(sync_df["offline_start_time"])
        & np.isfinite(sync_df["sync_start_time"])
        & (sync_df["offline_start_time"] >= 0)
        & (sync_df["sync_start_time"] >= 0)
        & (sync_df["sync_start_time"] >= sync_df["offline_start_time"])
    )
    sync_df = sync_df.loc[valid_timestamps].copy()

    sync_df["sync_delay"] = (
        sync_df["sync_start_time"] - sync_df["offline_start_time"]
    )

    terminal_ids = set(
        df.loc[
            df["event"].eq(completion_event[scenario]), "task_id"
        ].dropna().unique()
    )
    sync_df = sync_df.loc[sync_df["task_id"].isin(terminal_ids)].copy()
    sync_df = sync_df.sort_values(["task_id", "sync_start_time"])
    sync_df = sync_df.drop_duplicates(subset=["task_id"], keep="first")
    return sync_df["sync_delay"].to_numpy(dtype=float)


def _metric_values(df, metric, scenario=None):

    scenario = scenario.upper() if scenario else None

    if metric == "end_to_end_latency":

        completion_event = {
            "CLOUD_FIRST": "CLOUD_COMPLETE",
            "OFFLINE_DIRECT": "CLOUD_COMPLETE",
            "EDGE_ONLY": "EDGE_COMPLETE",
            "HES": "CLOUD_COMPLETE",
        }

        if scenario not in completion_event:
            return np.array([], dtype=float)

        event = completion_event[scenario]

        completed = df.loc[df["event"].eq(event)].copy()

        if scenario == "EDGE_ONLY":
            end_column = "edge_completion_time"
        else:
            end_column = "cloud_completion_time"

        start = pd.to_numeric(
            completed["creation_time"],
            errors="coerce"
        )

        end = pd.to_numeric(
            completed[end_column],
            errors="coerce"
        )

        valid = (
            start.notna()
            & end.notna()
            & np.isfinite(start)
            & np.isfinite(end)
            & (start >= 0)
            & (end >= 0)
            & (end >= start)
        )

        values = (end[valid] - start[valid]).to_numpy(dtype=float)

    elif metric == "sync_delay_completed":
        values = _completed_task_sync_delay(df, scenario)

    else:

        event_map = {
            "edge_processing_time": "EDGE_COMPLETE",
            "cloud_processing_time": "CLOUD_COMPLETE",
            "sync_delay": "SYNC",
            "edge_to_cloud_delay": "CLOUD_COMPLETE",
        }

        if metric not in event_map:
            raise ValueError(f"Métrica desconhecida: {metric}")

        event = event_map[metric]

        selected = df.loc[df["event"].eq(event)].copy()

        values = pd.to_numeric(
            selected[metric],
            errors="coerce"
        ).to_numpy(dtype=float)

    values = np.asarray(values, dtype=float)

    return values[
        np.isfinite(values) & (values >= 0)
    ]


def _mean_ci(values, confidence=0.95):
    """Mean and CI half-widths; NaN CI indicates fewer than two repetitions."""
    values = np.asarray(values, dtype=float)
    values = values[np.isfinite(values)]
    n = len(values)
    if n == 0:
        return np.nan, np.nan, np.nan, 0
    mean = float(np.mean(values))
    if n < 2:
        return mean, np.nan, np.nan, n

    sem = float(np.std(values, ddof=1) / np.sqrt(n))
    if sem == 0:
        return mean, 0.0, 0.0, n
    low, high = t.interval(confidence, df=n - 1, loc=mean, scale=sem)
    return mean, max(0.0, mean - low), max(0.0, high - mean), n


def plot_hes_metric(
    metric,
    y_label,
    output_name,
    scenarios=None,
    include_scenarios=None,
):
    """Plot one metric; aggregate tasks within each run and then runs."""
    config = get_configuration()
    folder_path = Path(config["folder_path"])
    num_iterations = int(config["num_iterations"])
    scenarios = list(config["scenario_types"] if scenarios is None else scenarios)

    if include_scenarios is not None:
        requested = set(include_scenarios)
        scenarios = [scenario for scenario in scenarios if scenario in requested]

    start = int(config["min_devices"])
    step = int(config["step_devices"])
    end = int(config["max_devices"])
    if num_iterations < 1 or step <= 0 or end < start:
        raise ValueError("Invalid repetitions or device range in config.py.")

    device_counts = np.arange(start, end + 1, step, dtype=int)
    simulation_means = np.full(
        (num_iterations, len(scenarios), len(device_counts)),
        np.nan,
        dtype=float,
    )
    observation_counts = np.zeros(
        (num_iterations, len(scenarios), len(device_counts)), dtype=int
    )

    for iteration in range(1, num_iterations + 1):
        for si, scenario in enumerate(scenarios):
            for di, devices in enumerate(device_counts):
                path = _find_hes_csv(folder_path, iteration, scenario, int(devices))
                if path is None:
                    print(
                        f"[WARNING] HES CSV not found or ambiguous: "
                        f"ite{iteration}, scenario={scenario}, devices={devices}"
                    )
                    continue
                try:
                    df = _calculate_basic_metrics(_read_hes_csv(path))
                    values = _metric_values(df, metric, scenario=scenario)
                    observation_counts[iteration - 1, si, di] = len(values)
                    if len(values):
                        simulation_means[iteration - 1, si, di] = float(np.mean(values))
                    else:
                        print(f"[WARNING] No valid observations for {metric}: {path}")
                    print(
                        f"[READ] metric={metric} iteration={iteration} "
                        f"scenario={scenario} devices={devices} "
                        f"n_tasks={len(values)} "
                        f"run_mean={simulation_means[iteration - 1, si, di]}"
                    )
                except (ValueError, OSError, pd.errors.ParserError, KeyError) as exc:
                    print(f"[ERROR] Could not process {path}: {exc}")

    means = np.full((len(scenarios), len(device_counts)), np.nan)
    ci_low = np.full_like(means, np.nan)
    ci_high = np.full_like(means, np.nan)
    valid_repetitions = np.zeros_like(means, dtype=int)

    for si, scenario in enumerate(scenarios):
        for di, devices in enumerate(device_counts):
            vals = simulation_means[:, si, di]
            mean, low, high, n = _mean_ci(vals)
            means[si, di] = mean
            ci_low[si, di] = low
            ci_high[si, di] = high
            valid_repetitions[si, di] = n
            if n < num_iterations:
                print(
                    f"[DIAGNOSTIC] {metric}: {scenario}, {devices} devices: "
                    f"{n}/{num_iterations} valid repetitions."
                )
            if n == 1:
                print(
                    f"[WARNING] CI unavailable for {metric}: {scenario}, "
                    f"{devices} devices (only one valid repetition)."
                )

    fig, ax = plt.subplots()
    position = config["figure_position"]
    fig.set_size_inches(position[2] / 2.54, position[3] / 2.54)
    plt.rcParams.update({
        "font.family": "DejaVu Sans",
        "axes.linewidth": 0.8,
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
    })

    font_sizes = config["font_sizes"]
    legends = dict(zip(config["scenario_types"], config["legends"]))
    line_styles = config.get("line_styles", ["-", "--", "-.", ":"])
    markers = config.get("markers", ["*", "o", "s", "v"])

    for si, scenario in enumerate(scenarios):
        global_idx = config["scenario_types"].index(scenario)
        color = (
            config["colors"][global_idx]
            if config.get("use_color", True)
            else "black"
        )
        label = legends.get(scenario, scenario)
        style = line_styles[global_idx % len(line_styles)]
        marker = markers[global_idx % len(markers)]

        if config.get("plot_confidence_interval", True):
            # NaN CI means it could not be estimated. Use zero only for plotting
            # the bar (not for statistics) and report the missing CI above.
            low = np.where(np.isfinite(ci_low[si]), ci_low[si], 0.0)
            high = np.where(np.isfinite(ci_high[si]), ci_high[si], 0.0)
            ax.errorbar(
                device_counts, means[si],
                yerr=np.vstack([low, high]),
                label=label, color=color, linestyle=style, marker=marker,
                linewidth=2.0, markersize=6, markerfacecolor="white",
                markeredgewidth=1.1, elinewidth=0.8, capsize=2, zorder=3,
            )
        else:
            ax.plot(
                device_counts, means[si], label=label, color=color,
                linestyle=style, marker=marker, linewidth=2.0,
                markersize=6, markerfacecolor="white",
            )

    ax.set_xlabel(config["x_axis_label"], fontsize=font_sizes[0])
    ax.set_ylabel(y_label, fontsize=font_sizes[0])
    ax.tick_params(axis="both", which="major", labelsize=font_sizes[2])
    ax.set_xlim(start - step * 0.15, end + step * 0.35)
    ax.set_xticks(device_counts)
    ax.set_ylim(bottom=0)
    ax.grid(True, linestyle="--", alpha=0.25)
    ax.legend(
        fontsize=font_sizes[1],
        loc="upper center",
        bbox_to_anchor=(0.5, -0.10),
        ncol=max(1, len(scenarios)),
        frameon=False,
        handlelength=2.5,
        handletextpad=0.6,
        columnspacing=1.2,
    )
    fig.tight_layout()
    fig.subplots_adjust(bottom=0.22)

    if config.get("save_figure_as_pdf", True):
        folder_path.mkdir(parents=True, exist_ok=True)
        output_path = folder_path / output_name
        fig.savefig(output_path, bbox_inches="tight")
        print(f"[SAVED] {output_path}")

    plt.show()
    plt.close(fig)

    return {
        "device_counts": device_counts,
        "means": means,
        "ci_low": ci_low,
        "ci_high": ci_high,
        "valid_repetitions": valid_repetitions,
        "observation_counts": observation_counts,
    }


def plot_hes_all_core_metrics():
    """Generate the six HESLogger plots, using the original metric definitions."""
    plot_hes_metric(
        "end_to_end_latency", "Average End-to-End Latency (s)",
        "HES_EndToEndLatency.pdf",
        include_scenarios=[
            "CLOUD_FIRST",
            "OFFLINE_DIRECT",
            "EDGE_ONLY",
            "HES",
        ],
    )
    plot_hes_metric(
        "edge_processing_time", "Average Edge Processing Time (s)",
        "HES_EdgeProcessingTime.pdf",
        include_scenarios=["EDGE_ONLY", "HES"],
    )
    plot_hes_metric(
        "cloud_processing_time", "Average Cloud Processing Time (s)",
        "HES_CloudProcessingTime.pdf",
        include_scenarios=["CLOUD_FIRST", "OFFLINE_DIRECT", "HES"],
    )
    plot_hes_metric(
        "sync_delay", "Average Synchronization Delay (s)",
        "HES_SyncDelay.pdf",
        include_scenarios=["CLOUD_FIRST", "OFFLINE_DIRECT", "EDGE_ONLY", "HES"],
    )
    plot_hes_metric(
        "sync_delay_completed", "Average Synchronization Delay for Completed Tasks (s)",
        "HES_SyncDelayCompleted.pdf",
        include_scenarios=["CLOUD_FIRST", "OFFLINE_DIRECT", "EDGE_ONLY", "HES"],
    )
    plot_hes_metric(
        "edge_to_cloud_delay", "Average Edge-to-Cloud Delay (s)",
        "HES_EdgeToCloudDelay.pdf",
        include_scenarios=["HES"],
    )


if __name__ == "__main__":
    plot_hes_all_core_metrics()

