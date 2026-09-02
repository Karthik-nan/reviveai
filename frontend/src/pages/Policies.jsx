import { useEffect, useState } from "react";
import {
  ShieldCheck,
  RefreshCw,
  AlertTriangle,
  CreditCard,
  UserCheck,
  ClipboardCheck,
  CheckCircle2,
} from "lucide-react";

import { getPolicies } from "../api/policyApi";

const strategyIcons = {
  RETRY_PAYMENT: RefreshCw,
  UPDATE_PAYMENT_METHOD: CreditCard,
  CUSTOMER_ACTION_REQUIRED: UserCheck,
  MANUAL_REVIEW: ClipboardCheck,
};

const formatStrategy = (strategy) => {
  if (!strategy) {
    return "Unknown";
  }

  return strategy
    .split("_")
    .map(
      (word) =>
        word.charAt(0) +
        word.slice(1).toLowerCase()
    )
    .join(" ");
};

const getStrategyClass = (strategy) => {
  switch (strategy) {
    case "RETRY_PAYMENT":
      return "policy-strategy policy-strategy-retry";

    case "UPDATE_PAYMENT_METHOD":
      return "policy-strategy policy-strategy-payment";

    case "CUSTOMER_ACTION_REQUIRED":
      return "policy-strategy policy-strategy-action";

    case "MANUAL_REVIEW":
      return "policy-strategy policy-strategy-manual";

    default:
      return "policy-strategy";
  }
};

function Policies() {
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadPolicies = async () => {
    try {
      setLoading(true);
      setError("");

      const data = await getPolicies();

      setPolicies(
        Array.isArray(data)
          ? data
          : []
      );
    } catch (err) {
      console.error(
        "Failed to load recovery policies:",
        err
      );

      setError(
        err.response?.data?.message ||
          "Unable to load recovery policies."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPolicies();
  }, []);

  return (
    <div className="policies-page">

      {/* =====================================================
          PAGE HEADER
      ===================================================== */}

      <div className="page-heading policies-page-heading">

        <div>
          <div className="eyebrow">
            Recovery Intelligence
          </div>

          <h1>
            Recovery Policies
          </h1>

          <p>
            Deterministic rules that govern automated revenue
            recovery decisions.
          </p>
        </div>

        <button
          className="analysis-button"
          onClick={loadPolicies}
          disabled={loading}
        >
          <RefreshCw
            size={16}
            className={loading ? "spin" : ""}
          />

          Refresh
        </button>

      </div>

      {/* =====================================================
          POLICY GUARD
      ===================================================== */}

      <div className="policy-guard-banner">

        <div className="policy-guard-icon">
          <ShieldCheck size={22} />
        </div>

        <div>
          <h3>
            Deterministic Policy Guard
          </h3>

          <p>
            AI recommendations cannot override deterministic
            business rules. Recovery decisions below the
            automation threshold are sent to manual review.
          </p>
        </div>

      </div>

      {/* =====================================================
          POLICY THRESHOLDS
      ===================================================== */}

      <div className="policy-threshold-grid">

        <div className="policy-threshold-card">

          <span className="policy-threshold-label">
            Automation Threshold
          </span>

          <strong>
            0.40
          </strong>

          <span>
            Minimum recovery probability for automated recovery
          </span>

        </div>

        <div className="policy-threshold-card">

          <span className="policy-threshold-label">
            High Priority
          </span>

          <strong>
            ≥ 0.80
          </strong>

          <span>
            Highest recovery priority
          </span>

        </div>

        <div className="policy-threshold-card">

          <span className="policy-threshold-label">
            Medium High
          </span>

          <strong>
            ≥ 0.60
          </strong>

          <span>
            Elevated recovery priority
          </span>

        </div>

        <div className="policy-threshold-card">

          <span className="policy-threshold-label">
            Medium
          </span>

          <strong>
            ≥ 0.40
          </strong>

          <span>
            Standard automated recovery priority
          </span>

        </div>

      </div>

      {/* =====================================================
          DETERMINISTIC RULES
      ===================================================== */}

      <div className="content-card">

        <div className="content-card-header">

          <div>
            <h2>
              Deterministic Recovery Rules
            </h2>

            <p>
              Payment failure conditions and their corresponding
              recovery strategies.
            </p>
          </div>

          <span className="policy-count">
            {policies.length}{" "}
            {policies.length === 1
              ? "rule"
              : "rules"}
          </span>

        </div>

        {/* ===================================================
            LOADING
        =================================================== */}

        {loading && (
          <div className="empty-state">

            <RefreshCw
              size={24}
              className="spin"
            />

            <p>
              Loading recovery policies...
            </p>

          </div>
        )}

        {/* ===================================================
            ERROR
        =================================================== */}

        {!loading && error && (
          <div className="error-state">

            <AlertTriangle size={24} />

            <p>
              {error}
            </p>

            <button
              className="secondary-button"
              onClick={loadPolicies}
            >
              Try Again
            </button>

          </div>
        )}

        {/* ===================================================
            EMPTY
        =================================================== */}

        {!loading &&
          !error &&
          policies.length === 0 && (
            <div className="empty-state">

              <ShieldCheck size={24} />

              <p>
                No recovery policies are currently available.
              </p>

            </div>
          )}

        {/* ===================================================
            POLICY LIST
        =================================================== */}

        {!loading &&
          !error &&
          policies.length > 0 && (
            <div className="policy-list">

              {policies.map((policy) => {

                const Icon =
                  strategyIcons[
                    policy.strategy
                  ] || ShieldCheck;

                return (
                  <div
                    className="policy-rule-card"
                    key={`${policy.errorCode}-${policy.strategy}`}
                  >

                    {/* RULE ICON */}

                    <div className="policy-rule-icon">
                      <Icon size={20} />
                    </div>

                    {/* RULE CONTENT */}

                    <div className="policy-rule-content">

                      <div className="policy-rule-top">

                        <div>

                          <span className="policy-error-code">
                            {policy.errorCode}
                          </span>

                          <h3>
                            {formatStrategy(
                              policy.strategy
                            )}
                          </h3>

                        </div>

                        <span
                          className={getStrategyClass(
                            policy.strategy
                          )}
                        >
                          {formatStrategy(
                            policy.strategy
                          )}
                        </span>

                      </div>

                      <p className="policy-description">
                        {policy.description}
                      </p>

                      <div className="policy-rule-footer">

                        <div>

                          <span>
                            Priority rule
                          </span>

                          <strong>
                            {policy.priorityRule}
                          </strong>

                        </div>

                        <CheckCircle2 size={18} />

                      </div>

                    </div>

                  </div>
                );
              })}

            </div>
          )}

      </div>

      {/* =====================================================
          MANUAL REVIEW PROTECTION
      ===================================================== */}

      <div className="policy-safety-card">

        <div className="policy-safety-icon">
          <AlertTriangle size={20} />
        </div>

        <div>

          <h3>
            Manual Review Protection
          </h3>

          <p>
            Cases with a recovery score below 0.40, missing
            recovery scores, invalid scores, or deterministic
            manual-review rules cannot be automatically recovered.
          </p>

        </div>

      </div>

    </div>
  );
}

export default Policies;