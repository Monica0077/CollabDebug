import { render, screen } from '@testing-library/react';
import NavBar from '../NavBar';
import { BrowserRouter } from 'react-router-dom';

test('shows login and register when not authenticated', () => {
  localStorage.removeItem('token');
  render(
    <BrowserRouter>
      <NavBar />
    </BrowserRouter>
  );
  expect(screen.getByText(/Login/i)).toBeInTheDocument();
  expect(screen.getByText(/Register/i)).toBeInTheDocument();
});

test('shows logout when authenticated', () => {
  localStorage.setItem('token', 'fake-token');
  render(
    <BrowserRouter>
      <NavBar />
    </BrowserRouter>
  );
  expect(screen.getByText(/Logout/i)).toBeInTheDocument();
});
