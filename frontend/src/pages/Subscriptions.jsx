import { useEffect, useState } from "react";

import {
  AlertTriangle,
  ArrowLeft,
  Calendar,
  CheckCircle2,
  CreditCard,
  RefreshCw,
  Search,
  User,
} from "lucide-react";

import {
  getSubscriptions,
  getSubscriptionById,
} from "../api/subscriptionApi";

import {
  getRecoveryCases,
} from "../api/recoveryCaseApi";


function Subscriptions({
  onSelectCase,
  initialSubscriptionId,
  onBackToCustomer,
}) {

  const [subscriptions, setSubscriptions] =
    useState([]);

  const [selectedSubscriptionId, setSelectedSubscriptionId] =
    useState(null);

  const [selectedSubscription, setSelectedSubscription] =
    useState(null);

  const [subscriptionRecoveryCases, setSubscriptionRecoveryCases] =
    useState([]);

  const [loading, setLoading] =
    useState(true);

  const [detailsLoading, setDetailsLoading] =
    useState(false);

  const [recoveryCasesLoading, setRecoveryCasesLoading] =
    useState(false);

  const [error, setError] =
    useState(null);

  const [detailsError, setDetailsError] =
    useState(null);

  const [search, setSearch] =
    useState("");


  /*
   * ========================================================
   * LOAD ALL SUBSCRIPTIONS
   * ========================================================
   */

  const loadSubscriptions = async () => {

    try {

      setLoading(true);

      setError(null);

      const data =
        await getSubscriptions();

      setSubscriptions(
        Array.isArray(data)
          ? data
          : []
      );

    } catch (err) {

      console.error(
        "Failed to load subscriptions:",
        err
      );

      setError(
        "Unable to load subscription data."
      );

    } finally {

      setLoading(false);

    }

  };


  /*
   * ========================================================
   * OPEN SUBSCRIPTION DETAILS
   * ========================================================
   */

  const openSubscription = async (id) => {

    try {

      setSelectedSubscriptionId(id);

      setSelectedSubscription(null);

      setSubscriptionRecoveryCases([]);

      setDetailsLoading(true);

      setRecoveryCasesLoading(true);

      setDetailsError(null);


      const [
        subscriptionData,
        recoveryCases,
      ] = await Promise.all([

        getSubscriptionById(id),

        getRecoveryCases(),

      ]);


      setSelectedSubscription(
        subscriptionData
      );


      const matchingCases =
        recoveryCases.filter(
          (recoveryCase) =>
            recoveryCase.subscriptionId === id
        );


      setSubscriptionRecoveryCases(
        matchingCases
      );


    } catch (err) {

      console.error(
        "Failed to load subscription details:",
        err
      );

      setDetailsError(
        "Unable to load subscription details."
      );

      setSubscriptionRecoveryCases([]);

    } finally {

      setDetailsLoading(false);

      setRecoveryCasesLoading(false);

    }

  };


  /*
   * ========================================================
   * INITIAL LOAD
   * ========================================================
   */

  useEffect(() => {

    loadSubscriptions();

  }, []);


  /*
   * ========================================================
   * OPEN SUBSCRIPTION FROM CUSTOMER DETAILS
   * ========================================================
   */

  useEffect(() => {

    if (initialSubscriptionId) {

      openSubscription(
        initialSubscriptionId
      );

    }

  }, [initialSubscriptionId]);


  /*
   * ========================================================
   * CLOSE SUBSCRIPTION DETAILS
   * ========================================================
   */

  const closeSubscription = () => {

    setSelectedSubscriptionId(null);

    setSelectedSubscription(null);

    setSubscriptionRecoveryCases([]);

    setDetailsError(null);

  };


  /*
   * ========================================================
   * FORMAT CURRENCY
   * ========================================================
   */

  const formatCurrency = (
    amount,
    currency
  ) => {

    if (currency === "INR") {

      return `₹${Number(
        amount || 0
      ).toLocaleString("en-IN", {

        minimumFractionDigits: 2,

        maximumFractionDigits: 2,

      })}`;

    }


    return `${currency || ""} ${Number(
      amount || 0
    ).toLocaleString("en-IN", {

      minimumFractionDigits: 2,

      maximumFractionDigits: 2,

    })}`;

  };


  /*
   * ========================================================
   * FORMAT PERCENT
   * ========================================================
   */

  const formatPercent = (score) => {

    if (
      score === null ||
      score === undefined
    ) {

      return "—";

    }


    return `${(
      Number(score) * 100
    ).toFixed(0)}%`;

  };


  /*
   * ========================================================
   * FORMAT DATE
   * ========================================================
   */

  const formatDate = (date) => {

    if (!date) {

      return "—";

    }


    const parsedDate =
      new Date(date);


    if (
      Number.isNaN(
        parsedDate.getTime()
      )
    ) {

      return "—";

    }


    return parsedDate.toLocaleDateString(
      "en-IN",
      {

        day: "2-digit",

        month: "short",

        year: "numeric",

      }
    );

  };


  /*
   * ========================================================
   * FORMAT DATE + TIME
   * ========================================================
   */

  const formatDateTime = (date) => {

    if (!date) {

      return "—";

    }


    const parsedDate =
      new Date(date);


    if (
      Number.isNaN(
        parsedDate.getTime()
      )
    ) {

      return "—";

    }


    return parsedDate.toLocaleString(
      "en-IN",
      {

        day: "2-digit",

        month: "short",

        year: "numeric",

        hour: "2-digit",

        minute: "2-digit",

      }
    );

  };


  /*
   * ========================================================
   * SUBSCRIPTION STATUS STYLE
   * ========================================================
   */

  const getStatusClass = (status) => {

    switch (status) {

      case "ACTIVE":

        return "subscription-status active";


      case "PAST_DUE":

        return "subscription-status past-due";


      case "CANCELLED":

        return "subscription-status cancelled";


      default:

        return "subscription-status";

    }

  };


  /*
   * ========================================================
   * RECOVERY CASE STATUS STYLE
   * ========================================================
   */

  const getRecoveryCaseStatusClass = (
    status
  ) => {

    switch (status) {

      case "OPEN":

        return "recovery-case-status open";


      case "IN_PROGRESS":

        return "recovery-case-status in-progress";


      case "RECOVERED":

        return "recovery-case-status recovered";


      case "FAILED":

        return "recovery-case-status failed";


      default:

        return "recovery-case-status";

    }

  };


  /*
   * ========================================================
   * SEARCH
   * ========================================================
   */

  const filteredSubscriptions =
    subscriptions.filter(
      (subscription) => {

        const query =
          search.toLowerCase().trim();


        if (!query) {

          return true;

        }


        return (

          subscription.externalSubscriptionId
            ?.toLowerCase()
            .includes(query)

          ||

          subscription.customerId
            ?.toLowerCase()
            .includes(query)

          ||

          subscription.status
            ?.toLowerCase()
            .includes(query)

        );

      }
    );


  /*
   * ========================================================
   * DETAILS PAGE
   * ========================================================
   */

  if (selectedSubscriptionId) {

    return (

      <SubscriptionDetails

        onSelectCase={
          onSelectCase
        }

        subscription={
          selectedSubscription
        }

        recoveryCases={
          subscriptionRecoveryCases
        }

        recoveryCasesLoading={
          recoveryCasesLoading
        }

        loading={
          detailsLoading
        }

        error={
          detailsError
        }

        onBack={
          onBackToCustomer ||
          closeSubscription
        }

        onRefresh={() =>
          openSubscription(
            selectedSubscriptionId
          )
        }

        formatCurrency={
          formatCurrency
        }

        formatPercent={
          formatPercent
        }

        formatDate={
          formatDate
        }

        formatDateTime={
          formatDateTime
        }

        getStatusClass={
          getStatusClass
        }

        getRecoveryCaseStatusClass={
          getRecoveryCaseStatusClass
        }

      />

    );

  }


  /*
   * ========================================================
   * SUBSCRIPTIONS LIST
   * ========================================================
   */

  return (

    <div>

      {/* PAGE HEADING */}

      <div className="page-heading">

        <div>

          <div className="eyebrow">
            Revenue Intelligence
          </div>

          <h1>
            Subscriptions
          </h1>

          <p>
            Monitor subscription health,
            billing activity and recovery risk.
          </p>

        </div>


        <button

          className="analysis-button"

          onClick={
            loadSubscriptions
          }

          disabled={loading}

        >

          <RefreshCw size={17} />

          {loading
            ? "Refreshing..."
            : "Refresh"}

        </button>

      </div>


      {/* ERROR */}

      {error && (

        <div className="error-card">

          <AlertTriangle
            size={20}
          />

          <span>
            {error}
          </span>

          <button
            onClick={
              loadSubscriptions
            }
          >
            Retry
          </button>

        </div>

      )}


      {/* SUBSCRIPTION PORTFOLIO */}

      {!error && (

        <div className="subscriptions-card">

          {/* HEADER */}

          <div className="card-header">

            <div>

              <h2>
                Subscription Portfolio
              </h2>

              <span>

                {subscriptions.length}{" "}

                subscription
                {subscriptions.length === 1
                  ? ""
                  : "s"}

              </span>

            </div>


            <div className="subscription-search">

              <Search size={16} />

              <input

                type="text"

                placeholder="Search subscriptions..."

                value={search}

                onChange={(event) =>
                  setSearch(
                    event.target.value
                  )
                }

              />

            </div>

          </div>


          {/* LOADING */}

          {loading && (

            <div className="loading-card">

              <div className="spinner" />

              Loading subscriptions...

            </div>

          )}


          {/* TABLE */}

          {!loading &&
            filteredSubscriptions.length >
              0 && (

              <div className="table-wrapper">

                <table className="subscriptions-table">

                  <thead>

                    <tr>

                      <th>
                        Subscription
                      </th>

                      <th>
                        Customer
                      </th>

                      <th>
                        Amount
                      </th>

                      <th>
                        Status
                      </th>

                      <th>
                        Risk Score
                      </th>

                      <th>
                        Next Billing
                      </th>

                      <th>
                        Created
                      </th>

                    </tr>

                  </thead>


                  <tbody>

                    {filteredSubscriptions.map(
                      (subscription) => (

                        <tr

                          key={
                            subscription.id
                          }

                          className="subscription-row"

                          onClick={() =>
                            openSubscription(
                              subscription.id
                            )
                          }

                        >

                          {/* SUBSCRIPTION */}

                          <td>

                            <div className="subscription-name">

                              <div className="subscription-icon">

                                <CreditCard
                                  size={16}
                                />

                              </div>


                              <div>

                                <strong>
                                  {
                                    subscription.externalSubscriptionId
                                  }
                                </strong>

                                <span>
                                  {
                                    subscription.id
                                  }
                                </span>

                              </div>

                            </div>

                          </td>


                          {/* CUSTOMER */}

                          <td>

                            <span className="customer-id">

                              {
                                subscription.customerId ||
                                "—"
                              }

                            </span>

                          </td>


                          {/* AMOUNT */}

                          <td>

                            <strong>

                              {formatCurrency(

                                subscription.amount,

                                subscription.currency

                              )}

                            </strong>

                          </td>


                          {/* STATUS */}

                          <td>

                            <span

                              className={
                                getStatusClass(
                                  subscription.status
                                )
                              }

                            >

                              {
                                subscription.status
                              }

                            </span>

                          </td>


                          {/* RISK */}

                          <td>

                            <div className="risk-cell">

                              <div className="risk-value">

                                {formatPercent(
                                  subscription.riskScore
                                )}

                              </div>


                              <div className="risk-bar">

                                <div

                                  className="risk-bar-fill"

                                  style={{

                                    width:
                                      subscription.riskScore ===
                                      null

                                        ? "0%"

                                        : `${Number(
                                            subscription.riskScore
                                          ) * 100}%`,

                                  }}

                                />

                              </div>

                            </div>

                          </td>


                          {/* NEXT BILLING */}

                          <td>

                            {formatDate(
                              subscription.nextBillingAt
                            )}

                          </td>


                          {/* CREATED */}

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


          {/* NO SEARCH RESULTS */}

          {!loading &&

            subscriptions.length > 0 &&

            filteredSubscriptions.length ===
              0 && (

              <div className="loading-card">

                <Search size={20} />

                No subscriptions match
                your search.

              </div>

            )}


          {/* EMPTY */}

          {!loading &&

            subscriptions.length ===
              0 && (

              <div className="loading-card">

                <CreditCard
                  size={20}
                />

                No subscriptions found.

              </div>

            )}

        </div>

      )}

    </div>

  );

}


/*
 * =========================================================
 * SUBSCRIPTION DETAILS COMPONENT
 * =========================================================
 */

function SubscriptionDetails({

  onSelectCase,

  subscription,

  recoveryCases,

  recoveryCasesLoading,

  loading,

  error,

  onBack,

  onRefresh,

  formatCurrency,

  formatPercent,

  formatDate,

  formatDateTime,

  getStatusClass,

  getRecoveryCaseStatusClass,

}) {

  return (

    <div>

      {/* PAGE HEADER */}

      <div className="page-heading">

        <div>

          <button

            className="back-button"

            onClick={onBack}

          >

            <ArrowLeft
              size={17}
            />

            Back to Subscriptions

          </button>


          <div className="eyebrow">
            Subscription Details
          </div>


          <h1>

            {subscription
              ? subscription.externalSubscriptionId
              : "Subscription"}

          </h1>


          <p>

            Detailed billing and recovery
            information for this subscription.

          </p>

        </div>


        <button

          className="analysis-button"

          onClick={onRefresh}

          disabled={loading}

        >

          <RefreshCw
            size={17}
          />

          {loading
            ? "Refreshing..."
            : "Refresh"}

        </button>

      </div>


      {/* LOADING */}

      {loading && (

        <div className="loading-card">

          <div className="spinner" />

          Loading subscription details...

        </div>

      )}


      {/* ERROR */}

      {error && !loading && (

        <div className="error-card">

          <AlertTriangle
            size={20}
          />

          <span>
            {error}
          </span>

          <button
            onClick={onRefresh}
          >
            Retry
          </button>

        </div>

      )}


      {/* DETAILS */}

      {subscription &&
        !loading &&
        !error && (

          <>

            {/* SUMMARY METRICS */}

            <section className="metrics-grid">

              <DetailMetric

                icon={
                  <CreditCard
                    size={19}
                  />
                }

                label="Amount"

                value={
                  formatCurrency(
                    subscription.amount,
                    subscription.currency
                  )
                }

              />


              <DetailMetric

                icon={
                  <CheckCircle2
                    size={19}
                  />
                }

                label="Status"

                value={

                  <span

                    className={
                      getStatusClass(
                        subscription.status
                      )
                    }

                  >

                    {
                      subscription.status
                    }

                  </span>

                }

              />


              <DetailMetric

                icon={
                  <AlertTriangle
                    size={19}
                  />
                }

                label="Risk Score"

                value={
                  formatPercent(
                    subscription.riskScore
                  )
                }

              />


              <DetailMetric

                icon={
                  <Calendar
                    size={19}
                  />
                }

                label="Next Billing"

                value={
                  formatDate(
                    subscription.nextBillingAt
                  )
                }

              />

            </section>


            {/* INFORMATION + RISK */}

            <section className="subscription-details-grid">

              {/* SUBSCRIPTION INFORMATION */}

              <div className="chart-card">

                <div className="card-header">

                  <div>

                    <h2>
                      Subscription Information
                    </h2>

                    <span>
                      Billing configuration
                    </span>

                  </div>

                </div>


                <div className="detail-list">

                  <DetailRow

                    label="Subscription ID"

                    value={
                      subscription.id
                    }

                  />


                  <DetailRow

                    label="External Subscription ID"

                    value={
                      subscription.externalSubscriptionId
                    }

                  />


                  <DetailRow

                    label="Customer ID"

                    value={
                      subscription.customerId ||
                      "—"
                    }

                  />


                  <DetailRow

                    label="Currency"

                    value={
                      subscription.currency
                    }

                  />


                  <DetailRow

                    label="Created"

                    value={
                      formatDateTime(
                        subscription.createdAt
                      )
                    }

                  />

                </div>

              </div>


              {/* RECOVERY RISK */}

              <div className="intelligence-card">

                <div className="card-header">

                  <div>

                    <h2>
                      Recovery Risk
                    </h2>

                    <span>
                      Current subscription signal
                    </span>

                  </div>


                  <div className="ai-icon">

                    <AlertTriangle
                      size={19}
                    />

                  </div>

                </div>


                <div className="risk-summary">

                  <div className="risk-score-large">

                    {formatPercent(
                      subscription.riskScore
                    )}

                  </div>


                  <div className="risk-bar-large">

                    <div

                      className="risk-bar-large-fill"

                      style={{

                        width:
                          subscription.riskScore ===
                          null

                            ? "0%"

                            : `${Number(
                                subscription.riskScore
                              ) * 100}%`,

                      }}

                    />

                  </div>


                  <p>

                    Risk score represents the
                    current subscription risk
                    signal used by ReviveAI's
                    recovery intelligence.

                  </p>

                </div>


                <div className="policy-guard">

                  <div className="policy-icon">

                    <User
                      size={18}
                    />

                  </div>


                  <div>

                    <strong>
                      Customer Linked
                    </strong>

                    <p>

                      This subscription is
                      associated with a
                      customer record.

                    </p>

                  </div>

                </div>

              </div>

            </section>


            {/* RECOVERY CASES */}

            <section className="chart-card subscription-recovery-card">

              <div className="card-header">

                <div>

                  <h2>
                    Recovery Cases
                  </h2>

                  <span>
                    Recovery activity linked
                    to this subscription
                  </span>

                </div>


                <div className="recovery-case-count">

                  {
                    recoveryCases.length
                  }

                </div>

              </div>


              {/* RECOVERY CASE LOADING */}

              {recoveryCasesLoading && (

                <div className="loading-card">

                  <div className="spinner" />

                  Loading recovery cases...

                </div>

              )}


              {/* NO CASES */}

              {!recoveryCasesLoading &&

                recoveryCases.length ===
                  0 && (

                <div className="loading-card">

                  <CheckCircle2
                    size={20}
                  />

                  No recovery cases linked
                  to this subscription.

                </div>

              )}


              {/* CASE TABLE */}

              {!recoveryCasesLoading &&

                recoveryCases.length >
                  0 && (

                <div className="table-wrapper">

                  <table className="subscriptions-table">

                    <thead>

                      <tr>

                        <th>
                          Recovery Case
                        </th>

                        <th>
                          Status
                        </th>

                        <th>
                          Amount at Risk
                        </th>

                        <th>
                          Recovered
                        </th>

                        <th>
                          Recovery Score
                        </th>

                        <th>
                          Created
                        </th>

                      </tr>

                    </thead>


                    <tbody>

                      {recoveryCases.map(
                        (recoveryCase) => (

                          <tr

                            key={
                              recoveryCase.id
                            }

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

                              <div className="recovery-case-name">

                                <div className="subscription-icon">

                                  <AlertTriangle
                                    size={16}
                                  />

                                </div>


                                <div>

                                  <strong>
                                    {
                                      recoveryCase.id
                                    }
                                  </strong>

                                  <span>

                                    Payment:{" "}

                                    {
                                      recoveryCase.failedPaymentId
                                    }

                                  </span>

                                </div>

                              </div>

                            </td>


                            {/* STATUS */}

                            <td>

                              <span

                                className={
                                  getRecoveryCaseStatusClass(
                                    recoveryCase.status
                                  )
                                }

                              >

                                {
                                  recoveryCase.status
                                }

                              </span>

                            </td>


                            {/* AMOUNT AT RISK */}

                            <td>

                              <strong>

                                {formatCurrency(

                                  recoveryCase.amountAtRisk,

                                  subscription.currency

                                )}

                              </strong>

                            </td>


                            {/* RECOVERED */}

                            <td>

                              <strong>

                                {formatCurrency(

                                  recoveryCase.amountRecovered,

                                  subscription.currency

                                )}

                              </strong>

                            </td>


                            {/* SCORE */}

                            <td>

                              <strong>

                                {formatPercent(

                                  recoveryCase.recoveryScore

                                )}

                              </strong>

                            </td>


                            {/* CREATED */}

                            <td>

                              {formatDate(

                                recoveryCase.createdAt

                              )}

                            </td>

                          </tr>

                        )
                      )}

                    </tbody>

                  </table>

                </div>

              )}

            </section>

          </>

        )}

    </div>

  );

}


/*
 * =========================================================
 * DETAIL METRIC
 * =========================================================
 */

function DetailMetric({
  icon,
  label,
  value,
}) {

  return (

    <div className="metric-card">

      <div className="metric-top">

        <div className="metric-icon">

          {icon}

        </div>

      </div>


      <div className="metric-label">

        {label}

      </div>


      <div className="metric-value">

        {value}

      </div>

    </div>

  );

}


/*
 * =========================================================
 * DETAIL ROW
 * =========================================================
 */

function DetailRow({
  label,
  value,
}) {

  return (

    <div className="detail-row">

      <span>
        {label}
      </span>

      <strong>
        {value}
      </strong>

    </div>

  );

}


export default Subscriptions;