import {
  Button,
  ButtonProps,
  IconButton,
  IconButtonProps,
  Tooltip,
  TooltipProps
} from '@mui/material';
import React from 'react';
import { forwardRef } from 'react';

type ActionButtonProps = {
  icon: React.ReactNode;
  title?: string;
  border?: boolean;
  tooltip?: string;
  tooltipProps?: Partial<TooltipProps>;
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
} & (ButtonProps | IconButtonProps);

export const ActionButton = forwardRef<HTMLButtonElement, ActionButtonProps>(
  ({ icon, title, border = true, tooltip, tooltipProps, onClick, ...props }, ref) => {
    const buttonStyle = {
      ...(border && { border: 1 })
    };

    const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
      if (onClick) {
        onClick(event);
      }
    };

    const buttonContent = title ? (
      <Button
        ref={ref}
        variant="text"
        color="inherit"
        size="large"
        startIcon={icon}
        onClick={handleClick}
        sx={{
          ...buttonStyle
        }}
        {...(props as ButtonProps)}>
        {title}
      </Button>
    ) : (
      <IconButton ref={ref} sx={buttonStyle} onClick={handleClick} {...(props as IconButtonProps)}>
        {icon}
      </IconButton>
    );

    if (tooltip) {
      return (
        <Tooltip title={tooltip} {...tooltipProps}>
          <span>{buttonContent}</span>
        </Tooltip>
      );
    }

    return buttonContent;
  }
);
