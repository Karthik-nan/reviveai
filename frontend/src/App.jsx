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
  Settings as SettingsIcon,
  ShieldCheck,
  Users,
  X,
  Zap,
} from "lucide-react";

import { getDashboardOverview,runRecoveryAnalysis } from "./api/dashboardApi";

import RecoveryCases from "./pages/RecoveryCases";
import RecoveryCaseDetails from "./pages/RecoveryCaseDetails";
import Subscriptions from "./pages/Subscriptions";
import Customers from "./pages/Customers";
import CustomerDetails from "./pages/CustomerDetails";
import Policies from "./pages/Policies";
import SettingsPage from "./pages/Settings";


import "./App.css";


function App() {
  const [currentPage, setCurrentPage] = useState("dashboard");

  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [runningAnalysis, setRunningAnalysis] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [selectedCaseId, setSelectedCaseId] = useState(null);

  const [selectedCustomerId, setSelectedCustomerId] = useState(null);

  const [selectedSubscriptionId, setSelectedSubscriptionId] =
    useState(null);

  // Stores the customer from which a subscription was opened
  const [subscriptionCustomerId, setSubscriptionCustomerId] =
    useState(null);


  /*
   * ========================================================
   * DASHBOARD
   * ========================================================
   */

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

  const handleRunRecoveryAnalysis = async () => {
  try {
    setRunningAnalysis(true);
    setError(null);

    await runRecoveryAnalysis();

    await loadDashboard();
  } catch (err) {
    console.error(
      "Failed to run recovery analysis:",
      err
    );

    setError(
      "Unable to run recovery analysis."
    );
  } finally {
    setRunningAnalysis(false);
  }
};


  /*
   * ========================================================
   * NAVIGATION
   * ========================================================
   */

  const navigateTo = (page) => {
    setCurrentPage(page);
    setSidebarOpen(false);

    if (page !== "recovery-cases") {
      setSelectedCaseId(null);
    }

    if (page !== "customers") {
      setSelectedCustomerId(null);
    }

    if (page !== "subscriptions") {
      setSelectedSubscriptionId(null);
      setSubscriptionCustomerId(null);
    }
  };


  /*
   * ========================================================
   * RECOVERY CASE NAVIGATION
   * ========================================================
   */

  const openRecoveryCase = (id) => {
    setSelectedCaseId(id);
    setCurrentPage("recovery-cases");
    setSidebarOpen(false);
  };


  const closeRecoveryCase = () => {
    setSelectedCaseId(null);
  };


  /*
   * ========================================================
   * CUSTOMER NAVIGATION
   * ========================================================
   */

  const openCustomer = (id) => {
    setSelectedCustomerId(id);
    setCurrentPage("customers");
    setSidebarOpen(false);
  };


  const closeCustomer = () => {
    setSelectedCustomerId(null);
  };


  /*
   * ========================================================
   * SUBSCRIPTION NAVIGATION
   * ========================================================
   */

  const openSubscription = (id, customerId = null) => {
    setSelectedSubscriptionId(id);

    // Remember the customer when subscription was opened
    // from Customer Details.
    setSubscriptionCustomerId(customerId);

    setCurrentPage("subscriptions");
    setSidebarOpen(false);
  };


  const closeSubscription = () => {
    setSelectedSubscriptionId(null);
    setSubscriptionCustomerId(null);
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


  /*
   * ========================================================
   * RENDER
   * ========================================================
   */

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


        {/* ===================================================
            NAVIGATION
        =================================================== */}

        <nav className="navigation">

          {/* MAIN */}

          <div className="nav-section">

            <div className="nav-label">
              MAIN
            </div>


            {/* DASHBOARD */}

            <NavItem
              icon={<LayoutDashboard size={18} />}
              label="Dashboard"
              active={currentPage === "dashboard"}
              onClick={() => navigateTo("dashboard")}
            />


            {/* RECOVERY CASES */}

            <NavItem
              icon={<AlertTriangle size={18} />}
              label="Recovery Cases"
              active={currentPage === "recovery-cases"}
              onClick={() => navigateTo("recovery-cases")}
            />


            {/* SUBSCRIPTIONS */}

            <NavItem
              icon={<CreditCard size={18} />}
              label="Subscriptions"
              active={currentPage === "subscriptions"}
              onClick={() => navigateTo("subscriptions")}
            />


            {/* CUSTOMERS */}

            <NavItem
              icon={<Users size={18} />}
              label="Customers"
              active={currentPage === "customers"}
              onClick={() => navigateTo("customers")}
            />

          </div>


          {/* SYSTEM */}

          <div className="nav-section">

            <div className="nav-label">
              SYSTEM
            </div>


            {/* POLICIES */}

            <NavItem
              icon={<ShieldCheck size={18} />}
              label="Policies"
              active={currentPage === "policies"}
              onClick={() => navigateTo("policies")}
            />


            {/* SETTINGS */}

            <NavItem
              icon={<SettingsIcon size={18} />}
              label="Settings"
              active={currentPage === "settings"}
              onClick={() => navigateTo("settings")}
            />

          </div>

        </nav>


        {/* ===================================================
            SYSTEM STATUS
        =================================================== */}

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

        {/* ===================================================
            TOPBAR
        =================================================== */}

        <header className="topbar">

          <button
            className="mobile-menu"
            onClick={() => setSidebarOpen(true)}
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
             runningAnalysis={runningAnalysis}
             handleRunRecoveryAnalysis={handleRunRecoveryAnalysis}
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
            <Subscriptions
              onSelectCase={openRecoveryCase}
              initialSubscriptionId={selectedSubscriptionId}
              onBackToCustomer={() => {
                setSelectedSubscriptionId(null);

                // If subscription was opened from a customer,
                // return to that customer's details page.
                if (subscriptionCustomerId) {
                  setSelectedCustomerId(
                    subscriptionCustomerId
                  );

                  setSubscriptionCustomerId(null);

                  setCurrentPage("customers");

                  return;
                }

                // Otherwise return to the normal
                // subscriptions page.
                setCurrentPage("subscriptions");
              }}
            />
          )}


          {/* =================================================
              CUSTOMERS LIST
          ================================================= */}

          {currentPage === "customers" &&
            !selectedCustomerId && (
              <Customers
                onSelectCustomer={openCustomer}
              />
            )}


          {/* =================================================
              CUSTOMER DETAILS
          ================================================= */}

          {currentPage === "customers" &&
            selectedCustomerId && (
              <CustomerDetails
                id={selectedCustomerId}
                onBack={closeCustomer}
                onSelectSubscription={(subscriptionId) =>
                  openSubscription(
                    subscriptionId,
                    selectedCustomerId
                  )
                }
              />
            )}


          {/* =================================================
              POLICIES
          ================================================= */}

          {currentPage === "policies" && (
            <Policies />
          )}


          {/* =================================================
              SETTINGS
          ================================================= */}

          {currentPage === "settings" && (
            <SettingsPage />
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
  runningAnalysis,
  handleRunRecoveryAnalysis,
  formatCurrency,
  formatPercent,
}) {
  return (
    <>

      {/* ===================================================
          PAGE HEADING
      =================================================== */}

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
          onClick={handleRunRecoveryAnalysis}
           disabled={runningAnalysis}
           >
           <Activity size={17} />

            {runningAnalysis
             ? "Running Recovery Analysis..."
               : "Run Recovery Analysis"}
             </button>

      </div>


      {/* ===================================================
          LOADING
      =================================================== */}

      {loading && (
        <div className="loading-card">

          <div className="spinner" />

          Loading recovery intelligence...

        </div>
      )}


      {/* ===================================================
          ERROR
      =================================================== */}

      {error && (
        <div className="error-card">

          <AlertTriangle size={20} />

          <span>
            {error}
          </span>

          <button onClick={loadDashboard}>
            Retry
          </button>

        </div>
      )}


      {/* ===================================================
          DASHBOARD
      =================================================== */}

      {dashboard && !loading && (
        <>

          {/* =================================================
              KPI CARDS
          ================================================= */}

          <section className="metrics-grid">

            <MetricCard
              icon={<AlertTriangle size={19} />}
              label="Revenue at Risk"
              value={formatCurrency(
                dashboard.revenueAtRisk
              )}
              change="+12.4%"
              positive
              description="vs. previous period"
            />


            <MetricCard
              icon={<CheckCircle2 size={19} />}
              label="Revenue Recovered"
              value={formatCurrency(
                dashboard.revenueRecovered
              )}
              change="+18.7%"
              positive
              description="vs. previous period"
            />


            <MetricCard
              icon={<Activity size={19} />}
              label="Recovery Rate"
              value={formatPercent(
                dashboard.recoveryRate
              )}
              change="+5.2%"
              positive
              description="vs. previous period"
            />


            <MetricCard
              icon={<CreditCard size={19} />}
              label="Active Cases"
              value={dashboard.activeCases}
              change="-8.1%"
              description="vs. previous period"
            />

          </section>


          {/* =================================================
              MAIN DASHBOARD GRID
          ================================================= */}

          <section className="dashboard-grid">

            {/* =================================================
                REVENUE CHART
            ================================================= */}

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


              <RecoveryChart
                recoveryTrend={dashboard.recoveryTrend}
              />

            </div>


            {/* =================================================
                AI INTELLIGENCE
            ================================================= */}

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


              {/* POLICY GUARD */}

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
function RecoveryChart({
  recoveryTrend = [],
}) {
  /*
   * Build all 7 days, even when there was
   * no recovered revenue on a particular day.
   */
  const today = new Date();

  const last7Days = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today);

    date.setDate(today.getDate() - (6 - index));

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
  });

  /*
   * Match backend recovery data to each date.
   * Missing dates automatically become ₹0.
   */
  const chartData = last7Days.map((date) => {
    const recovery = recoveryTrend.find(
      (item) => item.date === date
    );

    return {
      date,
      amountRecovered: Number(
        recovery?.amountRecovered || 0
      ),
    };
  });

  const values = chartData.map(
    (item) => item.amountRecovered
  );

  const max = Math.max(...values, 1);


  const formatDay = (date) => {
    const parsedDate = new Date(
      `${date}T00:00:00`
    );

    return parsedDate.toLocaleDateString(
      "en-IN",
      {
        weekday: "short",
      }
    );
  };


  const formatCurrency = (value) => {
    return `₹${Number(value || 0).toLocaleString(
      "en-IN",
      {
        maximumFractionDigits: 0,
      }
    )}`;
  };


  return (
    <div className="chart">

      <div className="chart-area">

        <div className="y-axis">

          <span>
            {formatCurrency(max)}
          </span>

          <span>
            {formatCurrency(max * 0.75)}
          </span>

          <span>
            {formatCurrency(max * 0.5)}
          </span>

          <span>
            {formatCurrency(max * 0.25)}
          </span>

          <span>
            ₹0
          </span>

        </div>


        <div className="bars">

          {chartData.map((item) => {

            const value =
              item.amountRecovered;

            return (
              <div
                className="bar-column"
                key={item.date}
              >

                <div className="bar-wrapper">

                  {value > 0 && (
                    <div
                      className="bar"
                      style={{
                        height:
                          `${(value / max) * 100}%`,
                      }}
                      title={`${item.date}: ${formatCurrency(value)}`}
                    />
                  )}

                </div>


                <span>
                  {formatDay(item.date)}
                </span>

              </div>
            );

          })}

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