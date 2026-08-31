import { useEffect, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  RefreshCw,
} from "lucide-react";

import { getRecoveryCases } from "../api/recoveryCaseApi";

function RecoveryCases({ onSelectCase }) {
  const [cases, setCases] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  /*
   * ========================================================
   * LOAD RECOVERY CASES
   * ========================================================
   */

  const loadCases = async () => {
    try {
      setLoading(true);

      const data = await getRecoveryCases();

      setCases(Array.isArray(data) ? data : []);
      setError(null);
    } catch (err) {
      console.error(
        "Failed to load recovery cases:",
        err
      );

      setError(
        "Unable to load recovery cases."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCases();
  }, []);

  /*
   * ========================================================
   * FORMATTERS
   * ========================================================
   */

  const formatCurrency = (value) => {
    return `₹${Number(value || 0).toLocaleString(
      "en-IN",
      {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }
    )}`;
  };

  const formatScore = (value) => {
    return `${(
      Number(value || 0) * 100
    ).toFixed(0)}%`;
  };

  const formatDate = (value) => {
    if (!value) {
      return "-";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return "-";
    }

    return date.toLocaleDateString(
      "en-IN"
    );
  };

  /*
   * ========================================================
   * STATUS CLASS
   * ========================================================
   */

  const getStatusClass = (status) => {
    switch (status) {
      case "OPEN":
        return "status-open";

      case "IN_PROGRESS":
        return "status-progress";

      case "FAILED":
        return "status-failed";

      case "RECOVERED":
        return "status-recovered";

      default:
        return "";
    }
  };

  /*
   * ========================================================
   * STATUS ICON
   * ========================================================
   */

  const getStatusIcon = (status) => {
    switch (status) {
      case "OPEN":
        return (
          <AlertTriangle size={14} />
        );

      case "IN_PROGRESS":
        return (
          <Clock3 size={14} />
        );

      case "RECOVERED":
        return (
          <CheckCircle2 size={14} />
        );

      case "FAILED":
        return (
          <AlertTriangle size={14} />
        );

      default:
        return null;
    }
  };

  /*
   * ========================================================
   * LOADING STATE
   * ========================================================
   */

  if (loading) {
    return (
      <div className="page-state">

        <div className="spinner" />

        <p>
          Loading recovery cases...
        </p>

      </div>
    );
  }

  /*
   * ========================================================
   * ERROR STATE
   * ========================================================
   */

  if (error) {
    return (
      <div className="error-card">

        <AlertTriangle size={20} />

        <span>
          {error}
        </span>

        <button onClick={loadCases}>
          Retry
        </button>

      </div>
    );
  }

  /*
   * ========================================================
   * SUMMARY COUNTS
   * ========================================================
   */

  const totalCases = cases.length;

  const openCases = cases.filter(
    (item) =>
      item.status === "OPEN"
  ).length;

  const inProgressCases = cases.filter(
    (item) =>
      item.status === "IN_PROGRESS"
  ).length;

  const highPotentialCases = cases.filter(
    (item) =>
      item.recoveryPotential === "HIGH"
  ).length;

  /*
   * ========================================================
   * PAGE
   * ========================================================
   */

  return (
    <div className="recovery-cases-page">

      {/* ==================================================
          PAGE HEADER
      ================================================== */}

      <div className="page-heading">

        <div>

          <div className="eyebrow">
            Recovery Operations
          </div>

          <h1>
            Recovery Cases
          </h1>

          <p>
            Monitor failed payments and manage
            revenue recovery opportunities.
          </p>

        </div>

        <button
          className="analysis-button"
          onClick={loadCases}
        >
          <RefreshCw size={17} />

          Refresh Cases
        </button>

      </div>

      {/* ==================================================
          SUMMARY
      ================================================== */}

      <div className="cases-summary">

        <div className="case-summary-card">

          <span>
            Total Cases
          </span>

          <strong>
            {totalCases}
          </strong>

        </div>

        <div className="case-summary-card">

          <span>
            Open
          </span>

          <strong>
            {openCases}
          </strong>

        </div>

        <div className="case-summary-card">

          <span>
            In Progress
          </span>

          <strong>
            {inProgressCases}
          </strong>

        </div>

        <div className="case-summary-card">

          <span>
            High Potential
          </span>

          <strong>
            {highPotentialCases}
          </strong>

        </div>

      </div>

      {/* ==================================================
          RECOVERY QUEUE
      ================================================== */}

      <div className="cases-card">

        <div className="card-header">

          <div>

            <h2>
              Recovery Queue
            </h2>

            <span>
              {cases.length} recovery{" "}
              {cases.length === 1
                ? "opportunity"
                : "opportunities"}
            </span>

          </div>

        </div>

        <div className="cases-table-wrapper">

          <table className="cases-table">

            <thead>

              <tr>

                <th>
                  Case
                </th>

                <th>
                  Amount at Risk
                </th>

                <th>
                  Recovery Score
                </th>

                <th>
                  Potential
                </th>

                <th>
                  Status
                </th>

                <th>
                  Created
                </th>

              </tr>

            </thead>

            <tbody>

              {cases.length === 0 ? (

                <tr>

                  <td
                    colSpan="6"
                    className="empty-table"
                  >
                    No recovery cases found.
                  </td>

                </tr>

              ) : (

                cases.map(
                  (recoveryCase) => {

                    const score =
                      Number(
                        recoveryCase.recoveryScore ||
                          0
                      ) * 100;

                    const potential =
                      recoveryCase.recoveryPotential ||
                      "UNKNOWN";

                    const status =
                      recoveryCase.status ||
                      "UNKNOWN";

                    return (
                      <tr
                        key={recoveryCase.id}
                        className="clickable-case-row"
                        onClick={() =>
                          onSelectCase &&
                          onSelectCase(
                            recoveryCase.id
                          )
                        }
                      >

                        {/* CASE */}

                        <td>

                          <div className="case-id">

                            <strong>
                              {recoveryCase.id
                                ? `${recoveryCase.id.slice(
                                    0,
                                    8
                                  )}...`
                                : "Unknown"}
                            </strong>

                            <span>
                              Payment{" "}
                              {recoveryCase.failedPaymentId
                                ? `${recoveryCase.failedPaymentId.slice(
                                    0,
                                    8
                                  )}...`
                                : "Unknown"}
                            </span>

                          </div>

                        </td>

                        {/* AMOUNT */}

                        <td>

                          <strong>
                            {formatCurrency(
                              recoveryCase.amountAtRisk
                            )}
                          </strong>

                        </td>

                        {/* SCORE */}

                        <td>

                          <div className="score">

                            <div className="score-bar">

                              <div
                                className="score-fill"
                                style={{
                                  width: `${Math.min(
                                    Math.max(
                                      score,
                                      0
                                    ),
                                    100
                                  )}%`,
                                }}
                              />

                            </div>

                            <span>
                              {formatScore(
                                recoveryCase.recoveryScore
                              )}
                            </span>

                          </div>

                        </td>

                        {/* POTENTIAL */}

                        <td>

                          <span
                            className={`potential potential-${potential.toLowerCase()}`}
                          >
                            {potential}
                          </span>

                        </td>

                        {/* STATUS */}

                        <td>

                          <span
                            className={`case-status ${getStatusClass(
                              status
                            )}`}
                          >

                            {getStatusIcon(
                              status
                            )}

                            {status}

                          </span>

                        </td>

                        {/* CREATED */}

                        <td>

                          {formatDate(
                            recoveryCase.createdAt
                          )}

                        </td>

                      </tr>
                    );
                  }
                )
              )}

            </tbody>

          </table>

        </div>

      </div>

    </div>
  );
}

export default RecoveryCases;