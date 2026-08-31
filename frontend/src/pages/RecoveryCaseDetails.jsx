import { useEffect, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Clock3,
  CreditCard,
  ShieldCheck,
} from "lucide-react";

import { getRecoveryCaseById } from "../api/recoveryCaseApi";

function RecoveryCaseDetails({ id, onBack }) {
  const [recoveryCase, setRecoveryCase] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadCase();
  }, [id]);

  const loadCase = async () => {
    try {
      setLoading(true);

      const data = await getRecoveryCaseById(id);

      setRecoveryCase(data);
      setError(null);
    } catch (err) {
      console.error("Failed to load recovery case:", err);
      setError("Unable to load recovery case.");
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (value) => {
    return `₹${Number(value || 0).toLocaleString("en-IN", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const formatScore = (value) => {
    return `${(Number(value || 0) * 100).toFixed(0)}%`;
  };

  const formatDate = (value) => {
    if (!value) return "—";

    return new Date(value).toLocaleString("en-IN", {
      dateStyle: "medium",
      timeStyle: "short",
    });
  };

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

  if (loading) {
    return (
      <div className="page-state">
        <div className="spinner" />
        <p>Loading recovery case...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="error-card">
        <AlertTriangle size={20} />
        <span>{error}</span>

        <button onClick={loadCase}>
          Retry
        </button>
      </div>
    );
  }

  if (!recoveryCase) {
    return null;
  }

  return (
    <div className="recovery-details-page">

      {/* Back */}

      <button
        className="back-button"
        onClick={onBack}
      >
        <ArrowLeft size={17} />
        Back to Recovery Cases
      </button>

      {/* Heading */}

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
          {recoveryCase.status === "OPEN" && (
            <AlertTriangle size={14} />
          )}

          {recoveryCase.status === "IN_PROGRESS" && (
            <Clock3 size={14} />
          )}

          {recoveryCase.status === "RECOVERED" && (
            <CheckCircle2 size={14} />
          )}

          {recoveryCase.status}
        </span>

      </div>

      {/* Overview */}

      <section className="details-grid">

        <div className="detail-card">

          <div className="detail-card-header">
            <div className="detail-icon">
              <CreditCard size={18} />
            </div>

            <div>
              <h2>Financial Impact</h2>
              <span>Revenue recovery metrics</span>
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

        <div className="detail-card">

          <div className="detail-card-header">
            <div className="detail-icon">
              <ShieldCheck size={18} />
            </div>

            <div>
              <h2>Recovery Intelligence</h2>
              <span>AI recovery assessment</span>
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
              value={recoveryCase.recoveryPotential}
            />

          </div>

        </div>

      </section>

      {/* Recovery Score */}

      <section className="details-card">

        <div className="card-header">

          <div>
            <h2>Recovery Probability</h2>
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

      {/* Case Information */}

      <section className="details-card">

        <div className="card-header">

          <div>
            <h2>Case Information</h2>
            <span>Identifiers and timestamps</span>
          </div>

        </div>

        <div className="information-grid">

          <InfoRow
            label="Case ID"
            value={recoveryCase.id}
          />

          <InfoRow
            label="Subscription ID"
            value={recoveryCase.subscriptionId}
          />

          <InfoRow
            label="Failed Payment ID"
            value={recoveryCase.failedPaymentId}
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

      {/* Policy Guard */}

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
        {value}
      </strong>

    </div>
  );
}


export default RecoveryCaseDetails;