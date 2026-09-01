import { useEffect, useState } from "react";
import {
  Users,
  Search,
  RefreshCw,
  AlertCircle,
} from "lucide-react";

import { getCustomers } from "../api/customerApi";

function Customers({ onSelectCustomer }) {
  const [customers, setCustomers] = useState([]);
  const [filteredCustomers, setFilteredCustomers] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadCustomers = async () => {
    try {
      setLoading(true);
      setError("");

      const data = await getCustomers();

      setCustomers(data);
      setFilteredCustomers(data);
    } catch (err) {
      console.error("Failed to load customers:", err);
      setError("Failed to load customers.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCustomers();
  }, []);

  useEffect(() => {
    const term = searchTerm.trim().toLowerCase();

    if (!term) {
      setFilteredCustomers(customers);
      return;
    }

    const filtered = customers.filter(
      (customer) =>
        customer.externalCustomerId?.toLowerCase().includes(term) ||
        customer.email?.toLowerCase().includes(term)
    );

    setFilteredCustomers(filtered);
  }, [searchTerm, customers]);

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 2,
    }).format(amount ?? 0);
  };

  return (
    <div className="page-content">
      <div className="page-header">
        <div>
          <h1>Customers</h1>
          <p>View customers and their revenue recovery activity.</p>
        </div>

        <button
          className="secondary-button"
          onClick={loadCustomers}
        >
          <RefreshCw size={16} />
          Refresh
        </button>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-card-header">
            <span>Total Customers</span>
            <Users size={18} />
          </div>

          <div className="stat-value">
            {customers.length}
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-card-header">
            <span>Active Customers</span>
            <Users size={18} />
          </div>

          <div className="stat-value">
            {
              customers.filter(
                (customer) =>
                  customer.activeSubscriptions > 0
              ).length
            }
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-card-header">
            <span>Past Due Customers</span>
            <AlertCircle size={18} />
          </div>

          <div className="stat-value">
            {
              customers.filter(
                (customer) =>
                  customer.pastDueSubscriptions > 0
              ).length
            }
          </div>
        </div>
      </div>

      <div className="content-card">
        <div className="content-card-header">
          <div>
            <h2>Customer Directory</h2>

            <p>
              {filteredCustomers.length} customer
              {filteredCustomers.length !== 1 ? "s" : ""}
            </p>
          </div>

          <div className="search-box">
            <Search size={16} />

            <input
              type="text"
              placeholder="Search customer or email..."
              value={searchTerm}
              onChange={(event) =>
                setSearchTerm(event.target.value)
              }
            />
          </div>
        </div>

        {loading && (
          <div className="empty-state">
            Loading customers...
          </div>
        )}

        {!loading && error && (
          <div className="empty-state error-state">
            {error}
          </div>
        )}

        {!loading &&
          !error &&
          filteredCustomers.length === 0 && (
            <div className="empty-state">
              No customers found.
            </div>
          )}

        {!loading &&
          !error &&
          filteredCustomers.length > 0 && (
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Customer</th>
                    <th>Email</th>
                    <th>Subscriptions</th>
                    <th>Past Due</th>
                    <th>Recovery Cases</th>
                    <th>Revenue at Risk</th>
                    <th>Recovered</th>
                  </tr>
                </thead>

                <tbody>
                  {filteredCustomers.map((customer) => (
                    <tr
                      key={customer.id}
                      className="clickable-case-row"
                      onClick={() =>
                        onSelectCustomer &&
                        onSelectCustomer(customer.id)
                      }
                    >
                      <td>
                        <div className="customer-name">
                          {customer.externalCustomerId}
                        </div>
                      </td>

                      <td>{customer.email}</td>

                      <td>
                        <span className="table-number">
                          {customer.subscriptionCount}
                        </span>
                      </td>

                      <td>
                        <span
                          className={
                            customer.pastDueSubscriptions > 0
                              ? "status-badge status-danger"
                              : "status-badge status-success"
                          }
                        >
                          {customer.pastDueSubscriptions}
                        </span>
                      </td>

                      <td>
                        {customer.recoveryCaseCount}
                      </td>

                      <td>
                        {formatCurrency(
                          customer.revenueAtRisk
                        )}
                      </td>

                      <td>
                        {formatCurrency(
                          customer.revenueRecovered
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </div>
    </div>
  );
}

export default Customers;