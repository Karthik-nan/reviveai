import { useEffect, useState } from "react";
import {
  ArrowLeft,
  CreditCard,
  Mail,
  User,
  AlertTriangle,
  CheckCircle2,
  Clock3,
  RefreshCw,
} from "lucide-react";

import { getCustomerById } from "../api/customerApi";

function CustomerDetails({ id, onBack, onSelectSubscription }) {
  const [customer, setCustomer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadCustomer = async () => {
    try {
      setLoading(true);
      setError("");

      const data = await getCustomerById(id);

      setCustomer(data);
    } catch (err) {
      console.error("Failed to load customer:", err);
      setError("Failed to load customer details.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      loadCustomer();
    }
  }, [id]);

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 2,
    }).format(amount ?? 0);
  };

  const formatDate = (value) => {
    if (!value) {
      return "Not available";
    }

    return new Date(value).toLocaleString("en-IN", {
      dateStyle: "medium",
      timeStyle: "short",
    });
  };

  const formatRiskScore = (score) => {
    if (score === null || score === undefined) {
      return "N/A";
    }

    return `${(Number(score) * 100).toFixed(0)}%`;
  };

  if (loading) {
    return (
      <div className="page-content">
        <div className="loading-card">
          <RefreshCw size={18} />
          Loading customer details...
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page-content">
        <div className="page-heading">
          <button
            className="secondary-button"
            onClick={onBack}
          >
            <ArrowLeft size={16} />
            Back to Customers
          </button>
        </div>

        <div className="error-card">
          <AlertTriangle size={20} />
          <span>{error}</span>

          <button onClick={loadCustomer}>
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!customer) {
    return (
      <div className="page-content">
        <div className="empty-state">
          Customer not found.
        </div>
      </div>
    );
  }

  return (
    <div className="page-content">

      {/* =====================================================
          HEADER
      ===================================================== */}

      <div className="page-heading">

        <div>
          <button
            className="secondary-button"
            onClick={onBack}
          >
            <ArrowLeft size={16} />
            Back to Customers
          </button>

          <div className="eyebrow">
            Customer Intelligence
          </div>

          <h1>
            {customer.externalCustomerId}
          </h1>

          <p>
            Customer profile and revenue recovery activity.
          </p>
        </div>

      </div>

      {/* =====================================================
          CUSTOMER SUMMARY
      ===================================================== */}

      <section className="metrics-grid">

        <div className="metric-card">

          <div className="metric-top">
            <div className="metric-icon">
              <User size={19} />
            </div>
          </div>

          <div className="metric-label">
            Customer
          </div>

          <div className="metric-value customer-metric-value">
            {customer.externalCustomerId}
          </div>

          <div className="metric-description">
            External customer ID
          </div>

        </div>

        <div className="metric-card">

          <div className="metric-top">
            <div className="metric-icon">
              <CreditCard size={19} />
            </div>
          </div>

          <div className="metric-label">
            Subscriptions
          </div>

          <div className="metric-value">
            {customer.subscriptionCount}
          </div>

          <div className="metric-description">
            {customer.activeSubscriptions} active
          </div>

        </div>

        <div className="metric-card">

          <div className="metric-top">
            <div className="metric-icon">
              <AlertTriangle size={19} />
            </div>
          </div>

          <div className="metric-label">
            Revenue at Risk
          </div>

          <div className="metric-value">
            {formatCurrency(customer.revenueAtRisk)}
          </div>

          <div className="metric-description">
            Recovery exposure
          </div>

        </div>

        <div className="metric-card">

          <div className="metric-top">
            <div className="metric-icon">
              <CheckCircle2 size={19} />
            </div>
          </div>

          <div className="metric-label">
            Revenue Recovered
          </div>

          <div className="metric-value">
            {formatCurrency(customer.revenueRecovered)}
          </div>

          <div className="metric-description">
            Successfully recovered
          </div>

        </div>

      </section>

      {/* =====================================================
          CUSTOMER INFORMATION
      ===================================================== */}

      <section className="dashboard-grid">

        <div className="content-card">

          <div className="content-card-header">
            <div>
              <h2>Customer Information</h2>
              <p>Account details</p>
            </div>
          </div>

          <div className="detail-list">

            <div className="detail-row">
              <span>
                <User size={16} />
                Customer ID
              </span>

              <strong>
                {customer.externalCustomerId}
              </strong>
            </div>

            <div className="detail-row">
              <span>
                <Mail size={16} />
                Email
              </span>

              <strong>
                {customer.email}
              </strong>
            </div>

            <div className="detail-row">
              <span>
                <CreditCard size={16} />
                Total Subscriptions
              </span>

              <strong>
                {customer.subscriptionCount}
              </strong>
            </div>

            <div className="detail-row">
              <span>
                <CheckCircle2 size={16} />
                Active Subscriptions
              </span>

              <strong>
                {customer.activeSubscriptions}
              </strong>
            </div>

            <div className="detail-row">
              <span>
                <AlertTriangle size={16} />
                Past Due Subscriptions
              </span>

              <strong>
                {customer.pastDueSubscriptions}
              </strong>
            </div>

            <div className="detail-row">
              <span>
                <Clock3 size={16} />
                Customer Since
              </span>

              <strong>
                {formatDate(customer.createdAt)}
              </strong>
            </div>

          </div>

        </div>

        {/* ===================================================
            RECOVERY SUMMARY
        =================================================== */}

        <div className="content-card">

          <div className="content-card-header">
            <div>
              <h2>Recovery Summary</h2>
              <p>Revenue recovery activity</p>
            </div>
          </div>

          <div className="detail-list">

            <div className="detail-row">
              <span>
                Recovery Cases
              </span>

              <strong>
                {customer.recoveryCaseCount}
              </strong>
            </div>

            <div className="detail-row">
              <span>
                Revenue at Risk
              </span>

              <strong>
                {formatCurrency(customer.revenueAtRisk)}
              </strong>
            </div>

            <div className="detail-row">
              <span>
                Revenue Recovered
              </span>

              <strong>
                {formatCurrency(customer.revenueRecovered)}
              </strong>
            </div>

            <div className="detail-row">
              <span>
                Cancelled Subscriptions
              </span>

              <strong>
                {customer.cancelledSubscriptions}
              </strong>
            </div>

          </div>

        </div>

      </section>

      {/* =====================================================
          SUBSCRIPTIONS
      ===================================================== */}

      <div className="content-card subscription-recovery-card">

        <div className="content-card-header">

          <div>
            <h2>Subscriptions</h2>

            <p>
              Customer subscription history and risk
            </p>
          </div>

        </div>

        {customer.subscriptions?.length === 0 ? (
          <div className="empty-state">
            No subscriptions found.
          </div>
        ) : (
          <div className="table-container">

            <table className="data-table">

              <thead>
                <tr>
                  <th>Subscription</th>
                  <th>Status</th>
                  <th>Amount</th>
                  <th>Risk Score</th>
                  <th>Next Billing</th>
                  <th>Created</th>
                </tr>
              </thead>

              <tbody>

                {customer.subscriptions?.map(
                  (subscription) => (

                    <tr
                        key={subscription.id}
                         className="clickable-case-row"
                           onClick={() =>
                          onSelectSubscription &&
                        onSelectSubscription(subscription.id)
                         }
                        >

                      <td>
                        <div className="customer-name">
                          {subscription.externalSubscriptionId}
                        </div>
                      </td>

                      <td>
                        <span
                          className={
                            subscription.status === "ACTIVE"
                              ? "status-badge status-success"
                              : subscription.status === "PAST_DUE"
                              ? "status-badge status-danger"
                              : "status-badge"
                          }
                        >
                          {subscription.status}
                        </span>
                      </td>

                      <td>
                        {formatCurrency(
                          subscription.amount
                        )}
                      </td>

                      <td>
                        {formatRiskScore(
                          subscription.riskScore
                        )}
                      </td>

                      <td>
                        {formatDate(
                          subscription.nextBillingAt
                        )}
                      </td>

                      <td>
                        {formatDate(
                          subscription.createdAt
                        )}
                      </td>

                    </tr>

                  )
                )}

              </tbody>

            </table>

          </div>
        )}

      </div>

    </div>
  );
}

export default CustomerDetails;
