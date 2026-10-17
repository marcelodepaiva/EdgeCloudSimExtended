"""
Plot metrics stored in EdgeCloudSim semicolon-separated log files.

This module preserves the row/column selectors used by the original scripts,
but adds safer parsing, missing-value handling, confidence intervals based
on valid simulation repetitions, and diagnostics for incomplete data.
"""
from pathlib import Path
import warnings

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy.stats import t

from config import get_configuration


def _read_log_value(file_path, row_offset, column_offset):
    """Read one numeric cell from a semicolon-separated simulation log."""
    if row_offset < 0:
        raise ValueError("row_offset must be >= 0.")
    if column_offset < 1:
        raise ValueError("column_offset is 1-based and must be >= 1.")

    # skiprows preserves the original script's row-offset convention.
    data = pd.read_csv(
        file_path,
        sep=";",
        header=None,
        skiprows=row_offset,
        nrows=1,
        dtype=str,
        engine="python",
    )
    if data.empty:
        raise ValueError(
            f"No row available at row_offset={row_offset} in {file_path}"
        )
    if data.shape[1] < column_offset:
        raise ValueError(
            f"Requested column {column_offset}, but row has "
            f"only {data.shape[1]} columns: {file_path}"
        )

    raw = str(data.iloc[0, column_offset - 1]).strip()
    value = pd.to_numeric(raw, errors="coerce")
    if pd.isna(value) or not np.isfinite(float(value)):
        raise ValueError(
            f"Selected cell is not a finite numeric value "
            f"(row={row_offset}, column={column_offset}, value={raw!r})"
        )
    return float(value)


def _read_total_tasks(file_path):
    """
    Preserve the original 'percentage_of_all' convention:
    total_tasks = first value + second value on the row after the header.
    """
    data = pd.read_csv(
        file_path, sep=";", header=None, skiprows=1, nrows=1,
        dtype=str, engine="python"
    )
    if data.empty or data.shape[1] < 2:
        raise ValueError(f"Cannot read the first two task counts in {file_path}")
    first = pd.to_numeric(data.iloc[0, 0], errors="coerce")
    second = pd.to_numeric(data.iloc[0, 1], errors="coerce")
    if pd.isna(first) or pd.isna(second):
        raise ValueError(f"Task counts are not numeric in {file_path}")
    return float(first) + float(second)


def _mean_and_ci(values, confidence=0.95):
    """Mean and two-sided Student-t confidence interval half-widths."""
    vals = np.asarray(values, dtype=float)
    vals = vals[np.isfinite(vals)]
    n = len(vals)
    if n == 0:
        return np.nan, np.nan, np.nan, 0
    mean = float(np.mean(vals))
    if n < 2:
        # A CI cannot be estimated from one repetition.
        return mean, np.nan, np.nan, n

    sem = float(np.std(vals, ddof=1) / np.sqrt(n))
    if sem == 0:
        return mean, 0.0, 0.0, n
    low, high = t.interval(confidence, df=n - 1, loc=mean, scale=sem)
    return mean, max(0.0, mean - low), max(0.0, high - mean), n


def plot_generic_line(
    row_offset,
    column_offset,
    y_label,
    app_type="ALL_APPS",
    calculate_percentage=None,
    legend_pos="best",
    divisor=1,
    ignore_zero_values=False,
):
    """
    Generate a line plot from EdgeCloudSim logs.

    row_offset: zero-based count of lines skipped before reading the target row.
    column_offset: one-based column number, retained for compatibility.
    divisor: optional scaling divisor; must be positive.
    ignore_zero_values: if True, zeros are treated as missing observations.
    """
    config = get_configuration()
    folder_path = Path(config["folder_path"])
    num_iterations = int(config["num_iterations"])
    scenarios = list(config["scenario_types"])
    start_devices = int(config["min_devices"])
    step_devices = int(config["step_devices"])
    end_devices = int(config["max_devices"])

    if num_iterations < 1:
        raise ValueError("num_iterations must be at least 1.")
    if step_devices <= 0 or end_devices < start_devices:
        raise ValueError("Invalid device range in config.py.")
    if not np.isfinite(divisor) or divisor <= 0:
        raise ValueError("divisor must be a positive finite number.")

    device_counts = np.arange(
        start_devices, end_devices + 1, step_devices, dtype=int
    )
    # NaN initialization is essential: missing files must not be counted as 0.
    all_results = np.full(
        (num_iterations, len(scenarios), len(device_counts)),
        np.nan,
        dtype=float,
    )

    for iteration in range(1, num_iterations + 1):
        for si, scenario in enumerate(scenarios):
            for di, devices in enumerate(device_counts):
                filename = (f"SIMRESULT_SCHOOL_NETWORK_{scenario}_"f"{devices}DEVICES_{app_type}_GENERIC.log")
                file_path = folder_path / f"ite{iteration}" / filename
                try:
                    value = _read_log_value(file_path, row_offset, column_offset)

                    if calculate_percentage == "percentage_of_all":
                        total_tasks = _read_total_tasks(file_path)
                        value = (100.0 * value / total_tasks) if total_tasks > 0 else np.nan

                    value /= divisor
                    if ignore_zero_values and value == 0:
                        value = np.nan

                    all_results[iteration - 1, si, di] = value
                    print(
                        f"[READ] iteration={iteration} scenario={scenario} "
                        f"devices={devices} value={value:g} file={file_path}"
                    )
                except FileNotFoundError:
                    print(f"[WARNING] Missing log: {file_path}")
                except (ValueError, pd.errors.ParserError, OSError) as exc:
                    print(f"[WARNING] Could not use {file_path}: {exc}")

    means = np.full((len(scenarios), len(device_counts)), np.nan)
    ci_low = np.full_like(means, np.nan)
    ci_high = np.full_like(means, np.nan)
    valid_repetitions = np.zeros_like(means, dtype=int)

    for si in range(len(scenarios)):
        for di in range(len(device_counts)):
            mean, low, high, n = _mean_and_ci(all_results[:, si, di])
            means[si, di] = mean
            ci_low[si, di] = low
            ci_high[si, di] = high
            valid_repetitions[si, di] = n
            if n < num_iterations:
                print(
                    f"[DIAGNOSTIC] {scenarios[si]} / {device_counts[di]} devices: "
                    f"{n}/{num_iterations} valid repetitions."
                )

    # Plot
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
    legends = config["legends"]
    line_styles = config.get("line_styles", ["-", "--", "-.", ":"])
    markers = config.get("markers", ["*", "o", "s", "v"])

    for si, scenario in enumerate(scenarios):
        color = (
            config["colors"][si]
            if config.get("use_color", True)
            else "black"
        )
        y = means[si]
        label = legends[si] if si < len(legends) else scenario

        if config.get("plot_confidence_interval", True):
            # Matplotlib requires finite yerr. Hide CI where n < 2, but report
            # those cases above rather than implying a measured zero-width CI.
            low = np.where(np.isfinite(ci_low[si]), ci_low[si], 0.0)
            high = np.where(np.isfinite(ci_high[si]), ci_high[si], 0.0)
            ax.errorbar(
                device_counts, y,
                yerr=np.vstack([low, high]),
                label=label,
                color=color,
                linestyle=line_styles[si % len(line_styles)],
                marker=markers[si % len(markers)],
                linewidth=2.0,
                markersize=6,
                markerfacecolor="white",
                markeredgewidth=1.1,
                elinewidth=0.8,
                capsize=2,
                zorder=3,
            )
        else:
            ax.plot(
                device_counts, y,
                label=label,
                color=color,
                linestyle=line_styles[si % len(line_styles)],
                marker=markers[si % len(markers)],
                linewidth=2.0,
                markersize=6,
                markerfacecolor="white",
            )

    ax.set_xlabel(config["x_axis_label"], fontsize=font_sizes[0])
    ax.set_ylabel(y_label, fontsize=font_sizes[0])
    ax.tick_params(axis="both", which="major", labelsize=font_sizes[2])
    ax.set_xlim(
        start_devices - step_devices * 0.15,
        end_devices + step_devices * 0.35,
    )
    ax.set_xticks(device_counts)
    ax.set_ylim(bottom=0)
    ax.grid(True, linestyle="--", alpha=0.25)

    # Keep the original external legend layout; honor legend_pos only if caller
    # explicitly requests an in-axes location.
    if legend_pos in {"upper left", "upper right", "lower left", "lower right", "best"}:
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
    else:
        ax.legend(fontsize=font_sizes[1], loc=legend_pos, frameon=False)

    fig.tight_layout()
    fig.subplots_adjust(bottom=0.22)

    if config.get("save_figure_as_pdf", True):
        folder_path.mkdir(parents=True, exist_ok=True)
        filename = f"{row_offset}_{column_offset}_{app_type.replace(' ', '_')}.pdf"
        output_path = folder_path / filename
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
    }
