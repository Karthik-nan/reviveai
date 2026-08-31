import { useEffect, useState } from "react";
import {
  Activity,
  AlertTriangle,
  ArrowDownRight,
  ArrowUpRight,
  Bot,
  CheckCircle2,
  CreditCard,
  LayoutDashboard,
  Menu,
  Settings,
  ShieldCheck,
  Users,
  X,
  Zap,
} from "lucide-react";

import { getDashboardOverview } from "./api/dashboardApi";
import RecoveryCases from "./pages/RecoveryCases";
import RecoveryCaseDetails from "./pages/RecoveryCaseDetails";

import "./App.css";

function App() {
  const [currentPage, setCurrentPage] = useState("dashboard");

  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [selectedCaseId, setSelectedCaseId] = useState(null);

  useEffect(() => {
    if (currentPage === "dashboard") {
      loadDashboard();
    }
  }, [currentPage]);

  const loadDashboard = async () => {
    try {
      setLoading(true);

      const data = await getDashboardOverview();

      setDashboard(data);
      setError(null);
    } catch (err) {
      console.error("Failed to load dashboard:", err);

      setError("Unable to load dashboard data.");
    } finally {
      setLoading(false);
    }
  };

  /*
   * ========================================================
   * NAVIGATION
   * ========================================================
   */

  const navigateTo = (page) => {
    setCurrentPage(page);

    // Close mobile sidebar
    setSidebarOpen(false);

    // Clear selected recovery case
    if (page !== "recovery-cases") {
      setSelectedCaseId(null);
    }
  };

  /*
   * ========================================================
   * RECOVERY CASE NAVIGATION
   * ========================================================
   */

  const openRecoveryCase = (id) => {
    setSelectedCaseId(id);
  };

  const closeRecoveryCase = () => {
    setSelectedCaseId(null);
  };

  /*
   * ========================================================
   * FORMATTERS
   * ========================================================
   */

  const formatCurrency = (value) => {
    return `₹${Number(value || 0).toLocaleString("en-IN", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const formatPercent = (value) => {
    return `${Number(value || 0).toFixed(1)}%`;
  };

  return (
    <div className="app">

      {/* =====================================================
          MOBILE OVERLAY
      ===================================================== */}

      {sidebarOpen && (
        <div
          className="sidebar-overlay"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* =====================================================
          SIDEBAR
      ===================================================== */}

      <aside
        className={`sidebar ${
          sidebarOpen ? "sidebar-open" : ""
        }`}
      >

        {/* SIDEBAR HEADER */}

        <div className="sidebar-header">

          <div className="brand-icon">
            <Zap size={20} />
          </div>

          <div>
            <div className="brand-name">
              ReviveAI
            </div>

            <div className="brand-subtitle">
              Revenue Recovery
            </div>
          </div>

          <button
            className="mobile-close"
            onClick={() => setSidebarOpen(false)}
          >
            <X size={20} />
          </button>

        </div>

        {/* NAVIGATION */}

        <nav className="navigation">

          {/* MAIN */}

          <div className="nav-section">

            <div className="nav-label">
              MAIN
            </div>

            <NavItem
              icon={<LayoutDashboard size={18} />}
              label="Dashboard"
              active={
                currentPage === "dashboard"
              }
              onClick={() =>
                navigateTo("dashboard")
              }
            />

            <NavItem
              icon={<AlertTriangle size={18} />}
              label="Recovery Cases"
              active={
                currentPage === "recovery-cases"
              }
              onClick={() =>
                navigateTo("recovery-cases")
              }
            />

            <NavItem
              icon={<CreditCard size={18} />}
              label="Subscriptions"
              active={
                currentPage === "subscriptions"
              }
              onClick={() =>
                navigateTo("subscriptions")
              }
            />

            <NavItem
              icon={<Users size={18} />}
              label="Customers"
              active={
                currentPage === "customers"
              }
              onClick={() =>
                navigateTo("customers")
              }
            />

          </div>

          {/* SYSTEM */}

          <div className="nav-section">

            <div className="nav-label">
              SYSTEM
            </div>

            <NavItem
              icon={<ShieldCheck size={18} />}
              label="Policies"
              active={
                currentPage === "policies"
              }
              onClick={() =>
                navigateTo("policies")
              }
            />

            <NavItem
              icon={<Settings size={18} />}
              label="Settings"
              active={
                currentPage === "settings"
              }
              onClick={() =>
                navigateTo("settings")
              }
            />

          </div>

        </nav>

        {/* SYSTEM STATUS */}

        <div className="system-status">

          <div className="status-dot" />

          <div>

            <div className="status-title">
              System Operational
            </div>

            <div className="status-text">
              All services running
            </div>

          </div>

        </div>

      </aside>

      {/* =====================================================
          MAIN CONTENT
      ===================================================== */}

      <main className="main-content">

        {/* TOPBAR */}

        <header className="topbar">

          <button
            className="mobile-menu"
            onClick={() =>
              setSidebarOpen(true)
            }
          >
            <Menu size={22} />
          </button>

          <div className="topbar-spacer" />

          <div className="user-avatar">
            RA
          </div>

        </header>

        {/* ===================================================
            PAGE CONTENT
        =================================================== */}

        <div className="content">

          {/* =================================================
              DASHBOARD
          ================================================= */}

          {currentPage === "dashboard" && (
            <DashboardPage
              dashboard={dashboard}
              loading={loading}
              error={error}
              loadDashboard={loadDashboard}
              formatCurrency={formatCurrency}
              formatPercent={formatPercent}
            />
          )}

          {/* =================================================
              RECOVERY CASES LIST
          ================================================= */}

          {currentPage === "recovery-cases" &&
            !selectedCaseId && (
              <RecoveryCases
                onSelectCase={openRecoveryCase}
              />
            )}

          {/* =================================================
              RECOVERY CASE DETAILS
          ================================================= */}

          {currentPage === "recovery-cases" &&
            selectedCaseId && (
              <RecoveryCaseDetails
                id={selectedCaseId}
                onBack={closeRecoveryCase}
              />
            )}

          {/* =================================================
              SUBSCRIPTIONS
          ================================================= */}

          {currentPage === "subscriptions" && (
            <ComingSoonPage
              title="Subscriptions"
              description="Subscription monitoring will be available here."
              icon={<CreditCard size={22} />}
            />
          )}

          {/* =================================================
              CUSTOMERS
          ================================================= */}

          {currentPage === "customers" && (
            <ComingSoonPage
              title="Customers"
              description="Customer recovery intelligence will be available here."
              icon={<Users size={22} />}
            />
          )}

          {/* =================================================
              POLICIES
          ================================================= */}

          {currentPage === "policies" && (
            <ComingSoonPage
              title="Recovery Policies"
              description="Deterministic recovery policies will be managed here."
              icon={<ShieldCheck size={22} />}
            />
          )}

          {/* =================================================
              SETTINGS
          ================================================= */}

          {currentPage === "settings" && (
            <ComingSoonPage
              title="Settings"
              description="System configuration will be available here."
              icon={<Settings size={22} />}
            />
          )}

        </div>

      </main>

    </div>
  );
}


/* =========================================================
   DASHBOARD PAGE
========================================================= */

function DashboardPage({
  dashboard,
  loading,
  error,
  loadDashboard,
  formatCurrency,
  formatPercent,
}) {

  return (
    <>

      {/* PAGE HEADING */}

      <div className="page-heading">

        <div>

          <div className="eyebrow">
            Recovery Intelligence
          </div>

          <h1>
            Revenue Recovery Dashboard
          </h1>

          <p>
            Monitor failed payments, recovery opportunities
            and AI-driven decisions.
          </p>

        </div>

        <button
          className="analysis-button"
          onClick={loadDashboard}
        >
          <Activity size={17} />
          Run Recovery Analysis
        </button>

      </div>

      {/* LOADING */}

      {loading && (
        <div className="loading-card">

          <div className="spinner" />

          Loading recovery intelligence...

        </div>
      )}

      {/* ERROR */}

      {error && (
        <div className="error-card">

          <AlertTriangle size={20} />

          <span>
            {error}
          </span>

          <button
            onClick={loadDashboard}
          >
            Retry
          </button>

        </div>
      )}

      {/* DASHBOARD */}

      {dashboard && !loading && (
        <>

          {/* KPI CARDS */}

          <section className="metrics-grid">

            <MetricCard
              icon={
                <AlertTriangle size={19} />
              }
              label="Revenue at Risk"
              value={formatCurrency(
                dashboard.revenueAtRisk
              )}
              change="+12.4%"
              positive
              description="vs. previous period"
            />

            <MetricCard
              icon={
                <CheckCircle2 size={19} />
              }
              label="Revenue Recovered"
              value={formatCurrency(
                dashboard.revenueRecovered
              )}
              change="+18.7%"
              positive
              description="vs. previous period"
            />

            <MetricCard
              icon={
                <Activity size={19} />
              }
              label="Recovery Rate"
              value={formatPercent(
                dashboard.recoveryRate
              )}
              change="+5.2%"
              positive
              description="vs. previous period"
            />

            <MetricCard
              icon={
                <CreditCard size={19} />
              }
              label="Active Cases"
              value={dashboard.activeCases}
              change="-8.1%"
              description="vs. previous period"
            />

          </section>

          {/* MAIN GRID */}

          <section className="dashboard-grid">

            {/* CHART */}

            <div className="chart-card">

              <div className="card-header">

                <div>

                  <h2>
                    Revenue Recovery
                  </h2>

                  <span>
                    Last 7 days
                  </span>

                </div>

                <select defaultValue="7">

                  <option value="7">
                    Last 7 days
                  </option>

                  <option value="30">
                    Last 30 days
                  </option>

                  <option value="90">
                    Last 90 days
                  </option>

                </select>

              </div>

              <RecoveryChart />

            </div>

            {/* AI INTELLIGENCE */}

            <div className="intelligence-card">

              <div className="card-header">

                <div>

                  <h2>
                    AI Recovery Intelligence
                  </h2>

                  <span>
                    Current system signals
                  </span>

                </div>

                <div className="ai-icon">
                  <Bot size={19} />
                </div>

              </div>

              <div className="intelligence-list">

                <Insight
                  label="Average Recovery Probability"
                  value={formatPercent(
                    dashboard.averageRecoveryProbability
                  )}
                />

                <Insight
                  label="High-Risk Subscriptions"
                  value={
                    dashboard.highRiskSubscriptions
                  }
                />

                <Insight
                  label="Automated Recoveries"
                  value={
                    dashboard.automatedRecoveries
                  }
                />

                <Insight
                  label="Manual Reviews"
                  value={
                    dashboard.manualReviews
                  }
                />

              </div>

              <div className="policy-guard">

                <div className="policy-icon">
                  <ShieldCheck size={18} />
                </div>

                <div>

                  <strong>
                    Policy Guard Active
                  </strong>

                  <p>
                    AI recommendations are validated by
                    deterministic recovery policies.
                  </p>

                </div>

              </div>

            </div>

          </section>

        </>
      )}

    </>
  );
}


/* =========================================================
   NAV ITEM
========================================================= */

function NavItem({
  icon,
  label,
  active = false,
  onClick,
}) {

  return (
    <button
      className={`nav-item ${
        active ? "active" : ""
      }`}
      onClick={onClick}
    >
      {icon}

      <span>
        {label}
      </span>
    </button>
  );
}


/* =========================================================
   METRIC CARD
========================================================= */

function MetricCard({
  icon,
  label,
  value,
  change,
  positive,
  description,
}) {

  return (
    <div className="metric-card">

      <div className="metric-top">

        <div className="metric-icon">
          {icon}
        </div>

        <div
          className={`metric-change ${
            positive
              ? "positive"
              : "negative"
          }`}
        >

          {positive ? (
            <ArrowUpRight size={14} />
          ) : (
            <ArrowDownRight size={14} />
          )}

          {change}

        </div>

      </div>

      <div className="metric-label">
        {label}
      </div>

      <div className="metric-value">
        {value}
      </div>

      <div className="metric-description">
        {description}
      </div>

    </div>
  );
}


/* =========================================================
   INSIGHT
========================================================= */

function Insight({
  label,
  value,
}) {

  return (
    <div className="insight">

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
   RECOVERY CHART
========================================================= */

function RecoveryChart() {

  const values = [
    31,
    45,
    38,
    62,
    52,
    72,
    65,
  ];

  const days = [
    "Mon",
    "Tue",
    "Wed",
    "Thu",
    "Fri",
    "Sat",
    "Sun",
  ];

  const max = Math.max(...values);

  return (
    <div className="chart">

      <div className="chart-area">

        <div className="y-axis">

          <span>₹40K</span>
          <span>₹30K</span>
          <span>₹20K</span>
          <span>₹10K</span>
          <span>₹0</span>

        </div>

        <div className="bars">

          {values.map(
            (value, index) => (

              <div
                className="bar-column"
                key={days[index]}
              >

                <div className="bar-wrapper">

                  <div
                    className="bar"
                    style={{
                      height:
                        `${(value / max) * 100}%`,
                    }}
                  />

                </div>

                <span>
                  {days[index]}
                </span>

              </div>

            )
          )}

        </div>

      </div>

    </div>
  );
}


/* =========================================================
   COMING SOON PAGE
========================================================= */

function ComingSoonPage({
  title,
  description,
  icon,
}) {

  return (
    <div>

      <div className="page-heading">

        <div>

          <div className="eyebrow">
            ReviveAI
          </div>

          <h1>
            {title}
          </h1>

          <p>
            {description}
          </p>

        </div>

      </div>

      <div className="loading-card">

        {icon}

        <span>
          This section is ready for the next implementation phase.
        </span>

      </div>

    </div>
  );
}


export default App;