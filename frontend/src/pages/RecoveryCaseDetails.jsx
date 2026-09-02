import { useEffect, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Clock3,
  CreditCard,
  ShieldCheck,
  Zap,
} from "lucide-react";

import {
  getRecoveryCaseById,
  getRecoveryActions,
} from "../api/recoveryCaseApi";

function RecoveryCaseDetails({ id, onBack }) {
  const [recoveryCase, setRecoveryCase] = useState(null);
  const [recoveryActions, setRecoveryActions] = useState([]);

  const [loading, setLoading] = useState(true);
  const [actionsLoading, setActionsLoading] = useState(true);

  const [error, setError] = useState(null);
  const [actionsError, setActionsError] = useState(null);

  useEffect(() => {
    loadCase();
  }, [id]);

  const loadCase = async () => {
    try {
      setLoading(true);
      setActionsLoading(true);

      setError(null);
      setActionsError(null);

      /*
       * Load recovery case
       */
      const caseData = await getRecoveryCaseById(id);

      setRecoveryCase(caseData);

      /*
       * Load recovery actions for this case
       */
      try {
        const actionData = await getRecoveryActions(id);

        setRecoveryActions(
          Array.isArray(actionData)
            ? actionData
            : []
        );
      } catch (actionError) {
        console.error(
          "Failed to load recovery actions:",
          actionError
        );

        setRecoveryActions([]);

        setActionsError(
          "Unable to load recovery actions."
        );
      } finally {
        setActionsLoading(false);
      }
    } catch (err) {
      console.error(
        "Failed to load recovery case:",
        err
      );

      setError(
        "Unable to load recovery case."
      );

      setActionsLoading(false);
    } finally {
      setLoading(false);
    }
  };

  /*
   * =========================================================
   * FORMATTERS
   * =========================================================
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
  if (
    value === null ||
    value === undefined ||
    value === ""
  ) {
    return "—";
  }

  const numericValue = Number(value);

  if (Number.isNaN(numericValue)) {
    return "—";
  }

  return `${(
    numericValue * 100
  ).toFixed(0)}%`;
};

  const formatDate = (value) => {
    if (!value) {
      return "—";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return "—";
    }

    return date.toLocaleString(
      "en-IN",
      {
        dateStyle: "medium",
        timeStyle: "short",
      }
    );
  };

  /*
   * =========================================================
   * STATUS
   * =========================================================
   */

  const getStatusClass = (status) => {
    switch (status) {
      case "OPEN":
        return "status-open";

      case "IN_PROGRESS":
        return "status-progress";

      case "RECOVERED":
        return "status-recovered";

      case "FAILED":
        return "status-failed";

      default:
        return "";
    }
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case "OPEN":
        return <AlertTriangle size={14} />;

      case "IN_PROGRESS":
        return <Clock3 size={14} />;

      case "RECOVERED":
        return <CheckCircle2 size={14} />;

      case "FAILED":
        return <AlertTriangle size={14} />;

      default:
        return null;
    }
  };

  /*
   * =========================================================
   * ACTION STATUS
   * =========================================================
   */

  const getActionStatusClass = (status) => {
    switch (status) {
      case "EXECUTED":
        return "action-status-executed";

      case "PENDING":
        return "action-status-pending";

      case "FAILED":
        return "action-status-failed";

      default:
        return "";
    }
  };

  const getPriorityClass = (priority) => {
    switch (priority) {
      case "HIGH":
        return "action-priority-high";

      case "MEDIUM_HIGH":
        return "action-priority-medium-high";

      case "MEDIUM":
        return "action-priority-medium";

      case "LOW":
        return "action-priority-low";

      default:
        return "";
    }
  };

  /*
   * =========================================================
   * LOADING
   * =========================================================
   */

  if (loading) {
    return (
      <div className="page-state">
        <div className="spinner" />

        <p>
          Loading recovery case...
        </p>
      </div>
    );
  }

  /*
   * =========================================================
   * ERROR
   * =========================================================
   */

  if (error) {
    return (
      <div className="error-card">

        <AlertTriangle size={20} />

        <span>
          {error}
        </span>

        <button onClick={loadCase}>
          Retry
        </button>

      </div>
    );
  }

  if (!recoveryCase) {
    return null;
  }

  /*
   * =========================================================
   * PAGE
   * =========================================================
   */

  return (
    <div className="recovery-details-page">

      {/* =====================================================
          BACK
      ===================================================== */}

      <button
        className="back-button"
        onClick={onBack}
      >
        <ArrowLeft size={17} />

        Back to Recovery Cases
      </button>

      {/* =====================================================
          HEADING
      ===================================================== */}

      <div className="page-heading">

        <div>

          <div className="eyebrow">
            Recovery Operations
          </div>

          <h1>
            Recovery Case
          </h1>

          <p>
            Review recovery intelligence and case information.
          </p>

        </div>

        <span
          className={`case-status ${getStatusClass(
            recoveryCase.status
          )}`}
        >
          {getStatusIcon(
            recoveryCase.status
          )}

          {recoveryCase.status}
        </span>

      </div>

      {/* =====================================================
          OVERVIEW
      ===================================================== */}

      <section className="details-grid">

        {/* FINANCIAL IMPACT */}

        <div className="detail-card">

          <div className="detail-card-header">

            <div className="detail-icon">
              <CreditCard size={18} />
            </div>

            <div>

              <h2>
                Financial Impact
              </h2>

              <span>
                Revenue recovery metrics
              </span>

            </div>

          </div>

          <div className="detail-metrics">

            <DetailMetric
              label="Amount at Risk"
              value={formatCurrency(
                recoveryCase.amountAtRisk
              )}
            />

            <DetailMetric
              label="Amount Recovered"
              value={formatCurrency(
                recoveryCase.amountRecovered
              )}
            />

          </div>

        </div>

        {/* RECOVERY INTELLIGENCE */}

        <div className="detail-card">

          <div className="detail-card-header">

            <div className="detail-icon">
              <ShieldCheck size={18} />
            </div>

            <div>

              <h2>
                Recovery Intelligence
              </h2>

              <span>
                AI recovery assessment
              </span>

            </div>

          </div>

          <div className="detail-metrics">

            <DetailMetric
              label="Recovery Score"
              value={formatScore(
                recoveryCase.recoveryScore
              )}
            />

            <DetailMetric
              label="Recovery Potential"
              value={
                recoveryCase.recoveryPotential
              }
            />

          </div>

        </div>

      </section>

      {/* =====================================================
          RECOVERY PROBABILITY
      ===================================================== */}

      <section className="details-card">

        <div className="card-header">

          <div>

            <h2>
              Recovery Probability
            </h2>

            <span>
              Current probability of successful recovery
            </span>

          </div>

          <strong className="large-score">
            {formatScore(
              recoveryCase.recoveryScore
            )}
          </strong>

        </div>

        <div className="large-score-bar">

          <div
            className="large-score-fill"
            style={{
              width: `${
                Number(
                  recoveryCase.recoveryScore || 0
                ) * 100
              }%`,
            }}
          />

        </div>

      </section>

      {/* =====================================================
          RECOVERY ACTION
      ===================================================== */}

      <section className="details-card recovery-action-card">

        <div className="card-header">

          <div>

            <h2>
              Recovery Action
            </h2>

            <span>
              Decision generated by the recovery policy engine
            </span>

          </div>

          <div className="action-header-icon">
            <Zap size={18} />
          </div>

        </div>

        {/* ACTION LOADING */}

        {actionsLoading && (
          <div className="action-loading">

            <div className="spinner" />

            <span>
              Loading recovery action...
            </span>

          </div>
        )}

        {/* ACTION ERROR */}

        {!actionsLoading && actionsError && (
          <div className="action-error">

            <AlertTriangle size={18} />

            <span>
              {actionsError}
            </span>

          </div>
        )}

        {/* NO ACTION */}

        {!actionsLoading &&
          !actionsError &&
          recoveryActions.length === 0 && (

            <div className="no-action">

              <ShieldCheck size={20} />

              <span>
                No recovery action has been generated
                for this case.
              </span>

            </div>
          )}

        {/* ACTIONS */}

        {!actionsLoading &&
          !actionsError &&
          recoveryActions.length > 0 && (

            <div className="recovery-actions-list">

              {recoveryActions.map(
                (action) => (

                  <div
                    className="recovery-action"
                    key={action.id}
                  >

                    {/* ACTION TOP */}

                    <div className="recovery-action-top">

                      <div>

                        <div className="action-label">
                          Strategy
                        </div>

                        <strong className="action-strategy">
                          {action.strategy}
                        </strong>

                      </div>

                      <div className="action-badges">

                        <span
                          className={`action-priority ${getPriorityClass(
                            action.priority
                          )}`}
                        >
                          {action.priority}
                        </span>

                        <span
                          className={`action-status ${getActionStatusClass(
                            action.status
                          )}`}
                        >
                          {action.status}
                        </span>

                      </div>

                    </div>

                    {/* ACTION DETAILS */}

                    <div className="action-details">

                      <div className="action-detail">

                        <span>
                          Recovery Score
                        </span>

                        <strong>
                          {formatScore(
                            action.recoveryScore
                          )}
                        </strong>

                      </div>

                      <div className="action-detail">

                        <span>
                          Created
                        </span>

                        <strong>
                          {formatDate(
                            action.createdAt
                          )}
                        </strong>

                      </div>

                      <div className="action-detail">

                        <span>
                          Executed
                        </span>

                        <strong>
                          {formatDate(
                            action.executedAt
                          )}
                        </strong>

                      </div>

                    </div>

                    {/* REASON */}

                    {action.reason && (
                      <div className="action-reason">

                        <span>
                          Reason
                        </span>

                        <p>
                          {action.reason}
                        </p>

                      </div>
                    )}

                  </div>
                )
              )}

            </div>
          )}

      </section>

      {/* =====================================================
          CASE INFORMATION
      ===================================================== */}

      <section className="details-card">

        <div className="card-header">

          <div>

            <h2>
              Case Information
            </h2>

            <span>
              Identifiers and timestamps
            </span>

          </div>

        </div>

        <div className="information-grid">

          <InfoRow
            label="Case ID"
            value={recoveryCase.id}
          />

          <InfoRow
            label="Subscription ID"
            value={
              recoveryCase.subscriptionId
            }
          />

          <InfoRow
            label="Failed Payment ID"
            value={
              recoveryCase.failedPaymentId
            }
          />

          <InfoRow
            label="Created At"
            value={formatDate(
              recoveryCase.createdAt
            )}
          />

          <InfoRow
            label="Resolved At"
            value={formatDate(
              recoveryCase.resolvedAt
            )}
          />

          <InfoRow
            label="Status"
            value={recoveryCase.status}
          />

        </div>

      </section>

      {/* =====================================================
          POLICY GUARD
      ===================================================== */}

      <section className="policy-guard detail-policy">

        <div className="policy-icon">
          <ShieldCheck size={18} />
        </div>

        <div>

          <strong>
            Policy Guard Active
          </strong>

          <p>
            Recovery recommendations are validated against
            deterministic recovery policies before execution.
          </p>

        </div>

      </section>

    </div>
  );
}


/* =========================================================
   DETAIL METRIC
========================================================= */

function DetailMetric({
  label,
  value,
}) {
  return (
    <div className="detail-metric">

      <span>
        {label}
      </span>

      <strong>
        {value}
      </strong>

    </div>
  );
}


/* =========================================================
   INFORMATION ROW
========================================================= */

function InfoRow({
  label,
  value,
}) {
  return (
    <div className="info-row">

      <span>
        {label}
      </span>

      <strong>
        {value || "—"}
      </strong>

    </div>
  );
}


export default RecoveryCaseDetails;